package com.mariageplus.service;

import com.mariageplus.entity.AppSetting;
import com.mariageplus.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppSettingServiceTest {

    @Mock
    private AppSettingRepository repository;

    private AppSettingService service;

    @BeforeEach
    void setUp() {
        service = new AppSettingService(repository);
    }

    private AppSetting row(String value) {
        AppSetting s = new AppSetting();
        s.setSettingKey(AppSettingService.KEY_WHATSAPP_SENDING_ENABLED);
        s.setSettingValue(value);
        return s;
    }

    private AppSetting maxRow(String value) {
        AppSetting s = new AppSetting();
        s.setSettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS);
        s.setSettingValue(value);
        return s;
    }

    @Test
    void enabled_byDefault_whenRowMissing() {
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_SENDING_ENABLED))
                .thenReturn(Optional.empty());
        assertTrue(service.isWhatsappSendingEnabled());
    }

    @Test
    void disabled_whenValueFalse() {
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_SENDING_ENABLED))
                .thenReturn(Optional.of(row("false")));
        assertFalse(service.isWhatsappSendingEnabled());
    }

    @Test
    void enabled_whenValueTrue_ignoringCase() {
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_SENDING_ENABLED))
                .thenReturn(Optional.of(row("TRUE")));
        assertTrue(service.isWhatsappSendingEnabled());
    }

    @Test
    void toggle_persistsAndReturnsNewValue() {
        AppSetting row = row("true");
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_SENDING_ENABLED))
                .thenAnswer(inv -> Optional.of(row));
        when(repository.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        assertFalse(service.setWhatsappSendingEnabled(false));
        assertEquals("false", row.getSettingValue());
    }

    @Test
    void whatsappMaxReminders_null_whenRowMissing() {
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS))
                .thenReturn(Optional.empty());
        assertEquals(null, service.getWhatsappMaxReminders());
    }

    @Test
    void whatsappMaxReminders_parsesStoredValue() {
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS))
                .thenReturn(Optional.of(maxRow("5")));
        assertEquals(Integer.valueOf(5), service.getWhatsappMaxReminders());
    }

    @Test
    void whatsappMaxReminders_ignoresNegativeOrGarbage() {
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS))
                .thenReturn(Optional.of(maxRow("-2")));
        assertEquals(null, service.getWhatsappMaxReminders());
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS))
                .thenReturn(Optional.of(maxRow("abc")));
        assertEquals(null, service.getWhatsappMaxReminders());
    }

    @Test
    void whatsappMaxReminders_setPersistsValue() {
        AppSetting setting = new AppSetting();
        setting.setSettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS);
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS))
                .thenAnswer(inv -> Optional.of(setting));
        when(repository.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        assertEquals(Integer.valueOf(2), service.setWhatsappMaxReminders(2));
        assertEquals("2", setting.getSettingValue());
    }

    @Test
    void whatsappMaxReminders_resetNull_deletesRow() {
        AppSetting setting = maxRow("2");
        when(repository.findBySettingKey(AppSettingService.KEY_WHATSAPP_MAX_REMINDERS))
                .thenReturn(Optional.of(setting));
        assertEquals(null, service.setWhatsappMaxReminders(null));
        org.mockito.Mockito.verify(repository).delete(setting);
    }
}