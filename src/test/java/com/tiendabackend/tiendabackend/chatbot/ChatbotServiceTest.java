package com.tiendabackend.tiendabackend.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tiendabackend.tiendabackend.chatbot.telegram.TelegramUpdateParser;
import com.tiendabackend.tiendabackend.cliente.ClienteDTO;
import com.tiendabackend.tiendabackend.cliente.ClienteService;
import com.tiendabackend.tiendabackend.cliente.DatosClienteTelegram;
import com.tiendabackend.tiendabackend.common.almacenamiento.AlmacenamientoArchivos;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

class ChatbotServiceTest {

    private static final long TELEGRAM_USER_ID = 5551234567L;
    private static final long CLIENTE_ID = 42L;
    private static final long CONVERSACION_ID = 7L;

    private ClienteService clienteService;
    private ConversacionService conversacionService;
    private Conversacion conversacion;
    private MensajeRepository mensajeRepository;
    private AdjuntoRepository adjuntoRepository;
    private AlmacenamientoArchivos almacenamiento;
    private TelegramClient telegramClient;
    private ChatbotService chatbotService;

    // Copias del estado de cada save, para verificar la secuencia de estados
    private final List<Mensaje> mensajesGuardados = new ArrayList<>();
    private final List<EstadoAdjunto> estadosAdjunto = new ArrayList<>();
    private final List<Adjunto> adjuntosGuardados = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        clienteService = mock(ClienteService.class);
        conversacionService = mock(ConversacionService.class);
        conversacion = Conversacion.abrir(CLIENTE_ID, Instant.ofEpochSecond(1790000000));
        conversacion.setId(CONVERSACION_ID);
        when(conversacionService.asignarEntrante(anyLong(), any(Instant.class))).thenReturn(conversacion);
        mensajeRepository = mock(MensajeRepository.class);
        adjuntoRepository = mock(AdjuntoRepository.class);
        almacenamiento = mock(AlmacenamientoArchivos.class);
        telegramClient = mock(TelegramClient.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());

        when(clienteService.registrarOActualizarDesdeTelegram(any(DatosClienteTelegram.class)))
                .thenReturn(new ClienteDTO(CLIENTE_ID, TELEGRAM_USER_ID, "ana_x", "Ana", "Pérez", "es", Instant.now()));
        when(mensajeRepository.existsByClienteIdAndTelegramMessageId(anyLong(), anyLong())).thenReturn(false);
        when(mensajeRepository.saveAndFlush(any(Mensaje.class))).thenAnswer(inv -> {
            Mensaje m = inv.getArgument(0);
            m.setId(100L);
            mensajesGuardados.add(m);
            return m;
        });
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> {
            mensajesGuardados.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(adjuntoRepository.save(any(Adjunto.class))).thenAnswer(inv -> {
            Adjunto a = inv.getArgument(0);
            a.setId(200L);
            estadosAdjunto.add(a.getEstado());
            adjuntosGuardados.add(a);
            return a;
        });
        when(telegramClient.execute(any(SendMessage.class))).thenAnswer(inv -> {
            Message enviado = new Message();
            enviado.setMessageId(11);
            enviado.setDate(1790000010);
            return enviado;
        });

        chatbotService = new ChatbotService(clienteService, conversacionService, mensajeRepository, adjuntoRepository,
                almacenamiento, new RespuestaFija(), telegramClient, transactionManager);
    }

    private static final String FROM = """
            "from": {"id": 5551234567, "is_bot": false, "first_name": "Ana", "last_name": "Pérez",
                     "username": "ana_x", "language_code": "es"}""";
    private static final String CHAT_PRIVADO = """
            "chat": {"id": 5551234567, "first_name": "Ana", "type": "private"}""";

    private static Update mensajePrivado(String contenido) throws Exception {
        return TelegramUpdateParser.parsear("""
                {"update_id": 1, "message": {"message_id": 10, %s, %s, "date": 1790000000, %s}}
                """.formatted(FROM, CHAT_PRIVADO, contenido));
    }

    private static Update notaDeVoz() throws Exception {
        return mensajePrivado("""
                "caption": "escuchá",
                "voice": {"file_id": "AwACAgEAAxk", "file_unique_id": "AgADx", "duration": 5,
                          "mime_type": "audio/ogg", "file_size": 12345}""");
    }

    private Mensaje entrante() {
        return mensajesGuardados.stream().filter(m -> m.getDireccion() == Direccion.ENTRANTE).findFirst().orElseThrow();
    }

    private List<Mensaje> salientes() {
        return mensajesGuardados.stream().filter(m -> m.getDireccion() == Direccion.SALIENTE).toList();
    }

    private SendMessage respuestaEnviada() throws Exception {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        return captor.getValue();
    }

    @Test
    void mensajeDeTextoSePersisteYSeResponde() throws Exception {
        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        ArgumentCaptor<DatosClienteTelegram> datos = ArgumentCaptor.forClass(DatosClienteTelegram.class);
        verify(clienteService).registrarOActualizarDesdeTelegram(datos.capture());
        assertThat(datos.getValue().getTelegramUserId()).isEqualTo(TELEGRAM_USER_ID);
        assertThat(datos.getValue().getUsername()).isEqualTo("ana_x");

        Mensaje entrante = entrante();
        assertThat(entrante.getClienteId()).isEqualTo(CLIENTE_ID);
        assertThat(entrante.getTelegramMessageId()).isEqualTo(10L);
        assertThat(entrante.getTipo()).isEqualTo(TipoMensaje.TEXTO);
        assertThat(entrante.getTexto()).isEqualTo("hola");
        assertThat(entrante.getFechaTelegram()).isEqualTo(Instant.ofEpochSecond(1790000000));
        assertThat(entrante.getFechaRegistro()).isNotNull();

        SendMessage respuesta = respuestaEnviada();
        assertThat(respuesta.getChatId()).isEqualTo(String.valueOf(TELEGRAM_USER_ID));
        assertThat(respuesta.getText()).isEqualTo("mensaje recibido");

        assertThat(salientes()).singleElement().satisfies(s -> {
            assertThat(s.getClienteId()).isEqualTo(CLIENTE_ID);
            assertThat(s.getTelegramMessageId()).isEqualTo(11L);
            assertThat(s.getTipo()).isEqualTo(TipoMensaje.TEXTO);
            assertThat(s.getTexto()).isEqualTo("mensaje recibido");
            assertThat(s.getFechaTelegram()).isEqualTo(Instant.ofEpochSecond(1790000010));
        });
        verifyNoInteractions(adjuntoRepository, almacenamiento);
    }

    @Test
    void notaDeVozSeDescargaYQuedaOk() throws Exception {
        File archivo = new File();
        archivo.setFileId("AwACAgEAAxk");
        archivo.setFilePath("voice/file_1.oga");
        when(telegramClient.execute(any(GetFile.class))).thenReturn(archivo);
        InputStream audio = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(telegramClient.downloadFileAsStream(archivo)).thenReturn(audio);
        when(almacenamiento.guardar("voz", audio, "ogg")).thenReturn("voz/2026/09/26/abc.ogg");

        chatbotService.procesar(notaDeVoz());

        Mensaje entrante = entrante();
        assertThat(entrante.getTipo()).isEqualTo(TipoMensaje.VOZ);
        assertThat(entrante.getTexto()).isEqualTo("escuchá");

        assertThat(estadosAdjunto).containsExactly(EstadoAdjunto.PENDIENTE, EstadoAdjunto.OK);
        Adjunto adjunto = adjuntosGuardados.get(adjuntosGuardados.size() - 1);
        assertThat(adjunto.getMensaje()).isSameAs(entrante);
        assertThat(adjunto.getTipo()).isEqualTo(TipoAdjunto.VOZ);
        assertThat(adjunto.getTelegramFileId()).isEqualTo("AwACAgEAAxk");
        assertThat(adjunto.getTelegramFileUniqueId()).isEqualTo("AgADx");
        assertThat(adjunto.getMimeType()).isEqualTo("audio/ogg");
        assertThat(adjunto.getTamanoBytes()).isEqualTo(12345L);
        assertThat(adjunto.getDuracionSeg()).isEqualTo(5);
        assertThat(adjunto.getClaveAlmacenamiento()).isEqualTo("voz/2026/09/26/abc.ogg");

        assertThat(respuestaEnviada().getText()).isEqualTo("mensaje recibido");
        assertThat(salientes()).hasSize(1);
    }

    @Test
    void descargaFallidaDejaAdjuntoEnErrorYSeRespondeIgual() throws Exception {
        when(telegramClient.execute(any(GetFile.class))).thenThrow(new TelegramApiException("timeout"));

        chatbotService.procesar(notaDeVoz());

        assertThat(entrante().getTipo()).isEqualTo(TipoMensaje.VOZ);
        assertThat(estadosAdjunto).containsExactly(EstadoAdjunto.PENDIENTE, EstadoAdjunto.ERROR);
        Adjunto adjunto = adjuntosGuardados.get(adjuntosGuardados.size() - 1);
        assertThat(adjunto.getClaveAlmacenamiento()).isNull();
        assertThat(adjunto.getTelegramFileId()).isEqualTo("AwACAgEAAxk");
        verifyNoInteractions(almacenamiento);

        assertThat(respuestaEnviada().getText()).isEqualTo("mensaje recibido");
        assertThat(salientes()).hasSize(1);
    }

    @Test
    void fotoConCaptionSeRegistraComoNoSoportadaSinDescargar() throws Exception {
        chatbotService.procesar(mensajePrivado("""
                "caption": "este modelo",
                "photo": [{"file_id": "p2", "file_unique_id": "u2", "width": 800, "height": 800}]"""));

        Mensaje entrante = entrante();
        assertThat(entrante.getTipo()).isEqualTo(TipoMensaje.NO_SOPORTADO);
        assertThat(entrante.getTexto()).isEqualTo("este modelo");
        verify(telegramClient, never()).execute(any(GetFile.class));
        verifyNoInteractions(adjuntoRepository, almacenamiento);
        assertThat(respuestaEnviada().getText()).isEqualTo("mensaje recibido");
    }

    @Test
    void stickerSinTextoSeRegistraComoNoSoportado() throws Exception {
        chatbotService.procesar(mensajePrivado("""
                "sticker": {"file_id": "s1", "file_unique_id": "su1", "type": "regular",
                            "width": 512, "height": 512, "is_animated": false, "is_video": false}"""));

        assertThat(entrante().getTipo()).isEqualTo(TipoMensaje.NO_SOPORTADO);
        assertThat(entrante().getTexto()).isNull();
        assertThat(salientes()).hasSize(1);
    }

    @Test
    void mensajeDeGrupoSeIgnora() throws Exception {
        chatbotService.procesar(TelegramUpdateParser.parsear("""
                {"update_id": 2, "message": {"message_id": 14, %s, "date": 1790000004, "text": "hola grupo",
                 "chat": {"id": -1001234567890, "title": "Clientes", "type": "supergroup"}}}
                """.formatted(FROM)));

        verifyNoInteractions(clienteService, mensajeRepository, adjuntoRepository, almacenamiento, telegramClient);
    }

    @Test
    void mensajeEditadoSeIgnora() throws Exception {
        chatbotService.procesar(TelegramUpdateParser.parsear("""
                {"update_id": 3, "edited_message": {"message_id": 10, %s, %s, "date": 1790000000,
                 "edit_date": 1790000100, "text": "hola (editado)"}}
                """.formatted(FROM, CHAT_PRIVADO)));

        verifyNoInteractions(clienteService, mensajeRepository, adjuntoRepository, almacenamiento, telegramClient);
    }

    @Test
    void mensajeYaProcesadoNoSeRespondeDeNuevo() throws Exception {
        when(mensajeRepository.existsByClienteIdAndTelegramMessageId(CLIENTE_ID, 10L)).thenReturn(true);

        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verify(mensajeRepository, never()).saveAndFlush(any());
        verify(mensajeRepository, never()).save(any());
        verifyNoInteractions(telegramClient);
    }

    @Test
    void reentregaConcurrenteDetectadaPorLaUqNoSeResponde() throws Exception {
        when(mensajeRepository.saveAndFlush(any(Mensaje.class)))
                .thenThrow(new DataIntegrityViolationException("uk_mensaje_cliente_telegram_message"));

        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verifyNoInteractions(telegramClient);
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    void falloAlEnviarLaRespuestaNoRegistraSaliente() throws Exception {
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("Forbidden: bot was blocked"));

        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        assertThat(entrante().getTexto()).isEqualTo("hola");
        assertThat(salientes()).isEmpty();
        verify(telegramClient, times(1)).execute(any(SendMessage.class));
    }

    @Test
    void entranteYSalienteQuedanEnLaConversacionAsignada() throws Exception {
        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verify(conversacionService).asignarEntrante(CLIENTE_ID, Instant.ofEpochSecond(1790000000));
        assertThat(entrante().getConversacion()).isSameAs(conversacion);
        assertThat(salientes()).singleElement()
                .satisfies(s -> assertThat(s.getConversacion()).isSameAs(conversacion));
        verify(conversacionService).registrarSaliente(CONVERSACION_ID, Instant.ofEpochSecond(1790000010));
    }

    @Test
    void mensajeYaProcesadoNoSeAsignaAConversacion() throws Exception {
        when(mensajeRepository.existsByClienteIdAndTelegramMessageId(CLIENTE_ID, 10L)).thenReturn(true);

        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verifyNoInteractions(conversacionService);
    }

    @Test
    void reentregaConcurrenteNoRegistraActividadDeSaliente() throws Exception {
        when(mensajeRepository.saveAndFlush(any(Mensaje.class)))
                .thenThrow(new DataIntegrityViolationException("uk_mensaje_cliente_telegram_message"));

        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verify(conversacionService, never()).registrarSaliente(anyLong(), any());
        verifyNoInteractions(telegramClient);
    }

    // Si la asignacion falla aun tras su reintento, el error NO se confunde con
    // una reentrega (que se descarta en silencio): se propaga y lo registra el
    // adaptador de ingreso.
    @Test
    void colisionAlAsignarConversacionNoSeTrataComoReentrega() {
        when(conversacionService.asignarEntrante(anyLong(), any(Instant.class)))
                .thenThrow(new DataIntegrityViolationException("conversacion_cliente_abierta"));

        assertThatThrownBy(() -> chatbotService.procesar(mensajePrivado("\"text\": \"hola\"")))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(mensajeRepository, never()).saveAndFlush(any());
    }

    @Test
    void falloAlEnviarNoRegistraActividadDeSaliente() throws Exception {
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("Forbidden"));

        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verify(conversacionService, never()).registrarSaliente(anyLong(), any());
    }

    @Test
    void extensionSegunMime() {
        assertThat(ChatbotService.extension("audio/ogg")).isEqualTo("ogg");
        assertThat(ChatbotService.extension("audio/mpeg")).isEqualTo("mp3");
        assertThat(ChatbotService.extension("application/x-raro")).isEqualTo("bin");
        assertThat(ChatbotService.extension(null)).isEqualTo("bin");
    }

    @Test
    void elUsuarioDelMensajeSeUsaComoDestinatario() throws Exception {
        chatbotService.procesar(mensajePrivado("\"text\": \"hola\""));

        verify(clienteService).registrarOActualizarDesdeTelegram(
                eq(new DatosClienteTelegram(TELEGRAM_USER_ID, "ana_x", "Ana", "Pérez", "es")));
    }
}
