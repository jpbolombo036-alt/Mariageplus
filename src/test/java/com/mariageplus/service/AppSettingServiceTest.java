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
}