package com.tiendabackend.tiendabackend.chatbot.telegram;

import com.tiendabackend.tiendabackend.chatbot.ChatbotService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequestMapping("/telegram")
@ConditionalOnExpression(CondicionesTelegram.MODO_WEBHOOK)
public class TelegramWebhookController {

    static final String HEADER_SECRET = "X-Telegram-Bot-Api-Secret-Token";

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final ChatbotService chatbotService;
    private final byte[] secret;

    public TelegramWebhookController(ChatbotService chatbotService, TelegramBotProperties properties) {
        this.chatbotService = chatbotService;
        // Este bean puede crearse antes que TelegramClient: se valida aqui
        // tambien para que un secret faltante se informe por su nombre.
        properties.validar();
        this.secret =properties.webhook().secret().getBytes(StandardCharsets.UTF_8);
    }

    // El cuerpo se recibe como String y se parsea con el ObjectMapper Jackson 2
    // de telegrambots (ver TelegramUpdateParser), no con el de Spring MVC.
    @PostMapping("/webhook")
    public ResponseEntity<Void> recibir(
            @RequestHeader(value = HEADER_SECRET, required = false) String secretRecibido,
            @RequestBody(required = false) String cuerpo) {

        if (!secretValido(secretRecibido)) {
            log.warn("Peticion al webhook de Telegram rechazada: secret ausente o invalido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Tras autenticar se responde 2xx aunque el procesamiento falle: si se
        // devolviera un error, Telegram reintentaria y retendria los updates
        // siguientes. Las reentregas legitimas las absorbe la deduplicacion.
        try {
            Update update = TelegramUpdateParser.parsear(cuerpo);
            chatbotService.procesar(update);
        } catch (Exception e) {
            log.error("Error procesando un update de Telegram recibido por webhook", e);
        }
        return ResponseEntity.ok().build();
    }

    // Comparacion en tiempo constante para no filtrar el secret por timing.
    private boolean secretValido(String recibido) {
        if (recibido == null) {
            return false;
        }
        return MessageDigest.isEqual(secret, recibido.getBytes(StandardCharsets.UTF_8));
    }
}
