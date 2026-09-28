package com.tiendabackend.tiendabackend.chatbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

class TelegramUpdateParserTest {

    private static final String CHAT_PRIVADO = """
            "chat": {"id": 5551234567, "first_name": "Ana", "username": "ana_x", "type": "private"}""";

    private static final String FROM = """
            "from": {"id": 5551234567, "is_bot": false, "first_name": "Ana", "last_name": "Pérez",
                     "username": "ana_x", "language_code": "es"}""";

    @Test
    void parseaMensajeDeTexto() throws Exception {
        Update update = TelegramUpdateParser.parsear("""
                {"update_id": 1001,
                 "message": {"message_id": 10, %s, %s, "date": 1790000000, "text": "hola",
                             "campo_nuevo_desconocido": {"x": 1}}}
                """.formatted(FROM, CHAT_PRIVADO));

        Message message = update.getMessage();
        assertThat(update.getUpdateId()).isEqualTo(1001);
        assertThat(message.getMessageId()).isEqualTo(10);
        assertThat(message.getChat().getType()).isEqualTo("private");
        assertThat(message.getChat().getId()).isEqualTo(5551234567L);
        assertThat(message.getFrom().getId()).isEqualTo(5551234567L);
        assertThat(message.getFrom().getUserName()).isEqualTo("ana_x");
        assertThat(message.getFrom().getLastName()).isEqualTo("Pérez");
        assertThat(message.getFrom().getLanguageCode()).isEqualTo("es");
        assertThat(message.getDate()).isEqualTo(1790000000);
        assertThat(message.getText()).isEqualTo("hola");
    }

    @Test
    void parseaNotaDeVozConCaption() throws Exception {
        Update update = TelegramUpdateParser.parsear("""
                {"update_id": 1002,
                 "message": {"message_id": 11, %s, %s, "date": 1790000001, "caption": "escuchá",
                             "voice": {"file_id": "AwACAgEAAxk", "file_unique_id": "AgADx", "duration": 5,
                                       "mime_type": "audio/ogg", "file_size": 12345}}}
                """.formatted(FROM, CHAT_PRIVADO));

        Message message = update.getMessage();
        assertThat(message.hasVoice()).isTrue();
        assertThat(message.hasText()).isFalse();
        assertThat(message.getCaption()).isEqualTo("escuchá");
        assertThat(message.getVoice().getFileId()).isEqualTo("AwACAgEAAxk");
        assertThat(message.getVoice().getFileUniqueId()).isEqualTo("AgADx");
        assertThat(message.getVoice().getDuration()).isEqualTo(5);
        assertThat(message.getVoice().getMimeType()).isEqualTo("audio/ogg");
        assertThat(message.getVoice().getFileSize()).isEqualTo(12345L);
    }

    @Test
    void parseaFotoConCaption() throws Exception {
        Update update = TelegramUpdateParser.parsear("""
                {"update_id": 1003,
                 "message": {"message_id": 12, %s, %s, "date": 1790000002, "caption": "este modelo",
                             "photo": [{"file_id": "p1", "file_unique_id": "u1", "width": 90, "height": 90},
                                       {"file_id": "p2", "file_unique_id": "u2", "width": 800, "height": 800}]}}
                """.formatted(FROM, CHAT_PRIVADO));

        Message message = update.getMessage();
        assertThat(message.hasPhoto()).isTrue();
        assertThat(message.hasVoice()).isFalse();
        assertThat(message.hasText()).isFalse();
        assertThat(message.getCaption()).isEqualTo("este modelo");
    }

    @Test
    void parseaSticker() throws Exception {
        Update update = TelegramUpdateParser.parsear("""
                {"update_id": 1004,
                 "message": {"message_id": 13, %s, %s, "date": 1790000003,
                             "sticker": {"file_id": "s1", "file_unique_id": "su1", "type": "regular",
                                         "width": 512, "height": 512, "is_animated": false, "is_video": false}}}
                """.formatted(FROM, CHAT_PRIVADO));

        Message message = update.getMessage();
        assertThat(message.hasSticker()).isTrue();
        assertThat(message.hasText()).isFalse();
        assertThat(message.getCaption()).isNull();
    }

    @Test
    void parseaMensajeDeGrupo() throws Exception {
        Update update = TelegramUpdateParser.parsear("""
                {"update_id": 1005,
                 "message": {"message_id": 14, %s, "date": 1790000004, "text": "hola grupo",
                             "chat": {"id": -1001234567890, "title": "Clientes", "type": "supergroup"}}}
                """.formatted(FROM));

        assertThat(update.getMessage().getChat().getType()).isEqualTo("supergroup");
        assertThat(update.getMessage().getChat().getId()).isEqualTo(-1001234567890L);
    }

    @Test
    void parseaMensajeEditado() throws Exception {
        Update update = TelegramUpdateParser.parsear("""
                {"update_id": 1006,
                 "edited_message": {"message_id": 10, %s, %s, "date": 1790000000,
                                    "edit_date": 1790000100, "text": "hola (editado)"}}
                """.formatted(FROM, CHAT_PRIVADO));

        assertThat(update.hasMessage()).isFalse();
        assertThat(update.hasEditedMessage()).isTrue();
        assertThat(update.getEditedMessage().getText()).isEqualTo("hola (editado)");
    }
}
