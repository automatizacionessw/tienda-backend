package com.tiendabackend.tiendabackend.chatbot;

import com.tiendabackend.tiendabackend.cliente.ClienteDTO;
import com.tiendabackend.tiendabackend.cliente.ClienteService;
import com.tiendabackend.tiendabackend.cliente.DatosClienteTelegram;
import com.tiendabackend.tiendabackend.common.almacenamiento.AlmacenamientoArchivos;
import java.io.InputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

// Flujo unico de procesamiento: lo invocan tanto el polling como el webhook.
// Ninguna transaccion queda abierta durante llamadas HTTP a Telegram
// (descarga del audio o envio de la respuesta); por eso se usan
// transacciones cortas con TransactionTemplate en vez de @Transactional.
@Service
@ConditionalOnProperty(prefix = "telegram.bot", name = "habilitado", havingValue = "true")
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private static final String CHAT_PRIVADO = "private";
    private static final String CATEGORIA_VOZ = "voz";

    private final ClienteService clienteService;
    private final MensajeRepository mensajeRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final AlmacenamientoArchivos almacenamiento;
    private final GeneradorRespuesta generadorRespuesta;
    private final TelegramClient telegramClient;
    private final TransactionTemplate transactionTemplate;

    public ChatbotService(ClienteService clienteService,
                          MensajeRepository mensajeRepository,
                          AdjuntoRepository adjuntoRepository,
                          AlmacenamientoArchivos almacenamiento,
                          GeneradorRespuesta generadorRespuesta,
                          TelegramClient telegramClient,
                          PlatformTransactionManager transactionManager) {
        this.clienteService = clienteService;
        this.mensajeRepository = mensajeRepository;
        this.adjuntoRepository = adjuntoRepository;
        this.almacenamiento = almacenamiento;
        this.generadorRespuesta = generadorRespuesta;
        this.telegramClient = telegramClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void procesar(Update update) {
        // Alcance de la iteracion: solo updates "message" de chats privados.
        // Grupos, canales, ediciones, callbacks, etc. se ignoran sin persistir.
        if (update == null || !update.hasMessage()) {
            log.debug("Update {} ignorado: no es de tipo message", update != null ? update.getUpdateId() : null);
            return;
        }
        Message message = update.getMessage();
        if (message.getChat() == null || !CHAT_PRIVADO.equals(message.getChat().getType())
                || message.getFrom() == null) {
            log.debug("Update {} ignorado: no proviene de un chat privado", update.getUpdateId());
            return;
        }

        ClienteDTO cliente = clienteService.registrarOActualizarDesdeTelegram(datosCliente(message.getFrom()));
        Long telegramMessageId = message.getMessageId().longValue();

        if (mensajeRepository.existsByClienteIdAndTelegramMessageId(cliente.getId(), telegramMessageId)) {
            log.debug("Mensaje {} del cliente {} ya procesado; se ignora la reentrega",
                    telegramMessageId, cliente.getId());
            return;
        }

        RegistroEntrante registro;
        try {
            registro = transactionTemplate.execute(estado -> guardarEntrante(cliente, message));
        } catch (DataIntegrityViolationException reentregaConcurrente) {
            // Otra entrega del mismo mensaje gano la carrera entre la consulta
            // previa y el INSERT: la UQ lo detecta y esta se descarta.
            log.debug("Mensaje {} del cliente {} ya registrado por una entrega concurrente",
                    telegramMessageId, cliente.getId());
            return;
        }

        if (registro.adjunto() != null) {
            descargarVoz(registro.adjunto(), message.getVoice());
        }

        responder(cliente, registro.mensaje());
    }

    private RegistroEntrante guardarEntrante(ClienteDTO cliente, Message message) {
        TipoMensaje tipo = clasificar(message);

        Mensaje mensaje = new Mensaje();
        mensaje.setClienteId(cliente.getId());
        mensaje.setTelegramMessageId(message.getMessageId().longValue());
        mensaje.setDireccion(Direccion.ENTRANTE);
        mensaje.setTipo(tipo);
        mensaje.setTexto(tipo == TipoMensaje.TEXTO ? message.getText() : message.getCaption());
        mensaje.setFechaTelegram(Instant.ofEpochSecond(message.getDate()));
        mensaje.setFechaRegistro(Instant.now());
        // saveAndFlush: una colision con la UQ se detecta aqui, dentro del try.
        Mensaje guardado = mensajeRepository.saveAndFlush(mensaje);

        Adjunto adjunto = null;
        if (tipo == TipoMensaje.VOZ) {
            Voice voice = message.getVoice();
            adjunto = new Adjunto();
            adjunto.setMensaje(guardado);
            adjunto.setTipo(TipoAdjunto.VOZ);
            adjunto.setTelegramFileId(voice.getFileId());
            adjunto.setTelegramFileUniqueId(voice.getFileUniqueId());
            adjunto.setMimeType(voice.getMimeType());
            adjunto.setTamanoBytes(voice.getFileSize());
            adjunto.setDuracionSeg(voice.getDuration());
            adjunto.setEstado(EstadoAdjunto.PENDIENTE);
            adjunto.setFechaRegistro(Instant.now());
            adjunto = adjuntoRepository.save(adjunto);
        }
        return new RegistroEntrante(guardado, adjunto);
    }

    // Solo texto y notas de voz se procesan; el resto (foto, audio, documento,
    // sticker...) queda registrado como NO_SOPORTADO y su archivo no se descarga.
    static TipoMensaje clasificar(Message message) {
        if (message.hasText()) {
            return TipoMensaje.TEXTO;
        }
        if (message.hasVoice()) {
            return TipoMensaje.VOZ;
        }
        return TipoMensaje.NO_SOPORTADO;
    }

    // Una descarga fallida no invalida el mensaje: el adjunto queda en ERROR
    // con su telegram_file_id (permite reintentarlo luego) y se responde igual.
    private void descargarVoz(Adjunto adjunto, Voice voice) {
        try {
            File archivo = telegramClient.execute(new GetFile(voice.getFileId()));
            try (InputStream contenido = telegramClient.downloadFileAsStream(archivo)) {
                String clave = almacenamiento.guardar(CATEGORIA_VOZ, contenido, extension(voice.getMimeType()));
                adjunto.setClaveAlmacenamiento(clave);
                adjunto.setEstado(EstadoAdjunto.OK);
            }
        } catch (Exception e) {
            log.warn("No se pudo descargar la nota de voz {} (adjunto {}): {}",
                    voice.getFileUniqueId(), adjunto.getId(), e.toString());
            adjunto.setClaveAlmacenamiento(null);
            adjunto.setEstado(EstadoAdjunto.ERROR);
        }
        transactionTemplate.executeWithoutResult(estado -> adjuntoRepository.save(adjunto));
    }

    private void responder(ClienteDTO cliente, Mensaje entrante) {
        String texto = generadorRespuesta.generar(MensajeEntranteDTO.desdeEntidad(entrante));

        Message enviado;
        try {
            enviado = telegramClient.execute(SendMessage.builder()
                    .chatId(cliente.getTelegramUserId())
                    .text(texto)
                    .build());
        } catch (Exception e) {
            // El entrante ya quedo persistido; sin confirmacion de Telegram no
            // hay message_id, asi que no se registra ningun saliente.
            log.warn("No se pudo enviar la respuesta al cliente {} (mensaje {}): {}",
                    cliente.getId(), entrante.getTelegramMessageId(), e.toString());
            return;
        }

        Mensaje saliente = new Mensaje();
        saliente.setClienteId(cliente.getId());
        saliente.setTelegramMessageId(enviado.getMessageId().longValue());
        saliente.setDireccion(Direccion.SALIENTE);
        saliente.setTipo(TipoMensaje.TEXTO);
        saliente.setTexto(texto);
        saliente.setFechaTelegram(enviado.getDate() != null
                ? Instant.ofEpochSecond(enviado.getDate()) : Instant.now());
        saliente.setFechaRegistro(Instant.now());
        transactionTemplate.executeWithoutResult(estado -> mensajeRepository.save(saliente));
    }

    private static DatosClienteTelegram datosCliente(User from) {
        return new DatosClienteTelegram(
                from.getId(),
                from.getUserName(),
                from.getFirstName(),
                from.getLastName(),
                from.getLanguageCode()
        );
    }

    static String extension(String mimeType) {
        if (mimeType == null) {
            return "bin";
        }
        return switch (mimeType.toLowerCase()) {
            case "audio/ogg", "audio/opus" -> "ogg";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            default -> "bin";
        };
    }

    private record RegistroEntrante(Mensaje mensaje, Adjunto adjunto) {
    }
}
