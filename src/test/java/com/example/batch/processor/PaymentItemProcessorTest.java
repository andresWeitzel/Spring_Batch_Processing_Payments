package com.example.batch.processor;

import com.example.batch.model.Payment;
import com.example.batch.enums.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentItemProcessorTest {
    
    private PaymentItemProcessor processor;
    private Payment validPayment;
    private StepExecution stepExecution;
    
    @BeforeEach
    void setUp() {
        processor = new PaymentItemProcessor();
        ReflectionTestUtils.setField(processor, "commissionRate", 0.02);
        ReflectionTestUtils.setField(processor, "minAmount", 10.0);
        ReflectionTestUtils.setField(processor, "maxAmount", 10000.0);
        ReflectionTestUtils.setField(processor, "supportedCurrencies", "USD,EUR,GBP,JPY");

        validPayment = new Payment();
        validPayment.setId(1L);
        validPayment.setAmount(new BigDecimal("100.00"));
        validPayment.setCurrency("USD");
        validPayment.setStatus("PENDING");
        validPayment.setPaymentDate(LocalDateTime.now());
        validPayment.setPaymentType(PaymentType.CREDIT_CARD);
        validPayment.setCustomerName("John Doe");
        validPayment.setCustomerEmail("john@example.com");

        // Configurar StepExecution para simular el paso 3
        stepExecution = MetaDataInstanceFactory.createStepExecution();
        processor.beforeStep(stepExecution);
    }
    
    @Test
    void process_validPayment_setsStatusProcessed() throws Exception {
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
        assertEquals("VALID", result.getValidationStatus());
        assertNotNull(result.getCommission());
        assertNotNull(result.getAmountInUSD());
    }
    
    @Test
    void process_amountBelowMinimum_rejectsPayment() throws Exception {
        validPayment.setAmount(new BigDecimal("5.00"));
        
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("INVALID", result.getStatus());
        assertTrue(result.getErrorMessage().contains("menor al mínimo permitido"));
    }
    
    @Test
    void process_amountAboveMaximum_rejectsPayment() throws Exception {
        validPayment.setAmount(new BigDecimal("15000.00"));
        
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("INVALID", result.getStatus());
        assertTrue(result.getErrorMessage().contains("mayor al máximo permitido"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "JPY"})
    void process_supportedCurrencies_acceptsPayment(String currency) throws Exception {
        validPayment.setCurrency(currency);
        
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
    }
    
    @Test
    void process_unsupportedCurrency_rejectsPayment() throws Exception {
        validPayment.setCurrency("MXN");
        
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("INVALID", result.getStatus());
        assertTrue(result.getErrorMessage().contains("Moneda no soportada"));
    }
    
    @Test
    void process_invalidEmail_rejectsPayment() throws Exception {
        validPayment.setCustomerEmail("invalid-email");
        
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("INVALID", result.getStatus());
        assertTrue(result.getErrorMessage().contains("Formato de email inválido"));
    }
    
    @Test
    void process_nullPaymentType_rejectsPayment() throws Exception {
        validPayment.setPaymentType(null);
        
        Payment result = processor.process(validPayment);
        
        assertNotNull(result);
        assertEquals("INVALID", result.getStatus());
        assertTrue(result.getErrorMessage().contains("Tipo de pago es requerido"));
    }
} 