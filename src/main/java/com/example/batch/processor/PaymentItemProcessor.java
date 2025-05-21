package com.example.batch.processor;

import com.example.batch.model.Payment;
import com.example.batch.enums.PaymentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PaymentItemProcessor implements ItemProcessor<Payment, Payment> {
    
    private static final Set<String> SUPPORTED_CURRENCIES = new HashSet<>(Arrays.asList("USD", "EUR", "GBP", "JPY"));
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");
    
    private double commissionRate;
    private double minAmount;
    private double maxAmount;
    private String supportedCurrencies;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    private List<String> currencies;
    private StepExecution stepExecution;

    public void setCommissionRate(double commissionRate) {
        this.commissionRate = commissionRate;
    }

    public void setMinAmount(double minAmount) {
        this.minAmount = minAmount;
    }

    public void setMaxAmount(double maxAmount) {
        this.maxAmount = maxAmount;
    }

    public void setSupportedCurrencies(String supportedCurrencies) {
        this.supportedCurrencies = supportedCurrencies;
    }
    
    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        this.currencies = Arrays.asList(supportedCurrencies.split(","));
    }
    
    @Override
    public Payment process(Payment payment) throws Exception {
        log.info("Procesando pago: {}", payment);
        try {
            validatePayment(payment);
            payment.setStatus("PROCESSED");
            payment.setValidationStatus("VALID");
            payment.setCommission(calculateCommission(payment.getAmount()));
            payment.setAmountInUSD(convertToUSD(payment.getAmount(), payment.getCurrency()));
            log.info("Pago procesado exitosamente: {}", payment);
        } catch (PaymentValidationException e) {
            payment.setStatus("INVALID");
            payment.setValidationStatus("INVALID");
            payment.setErrorMessage(e.getMessage());
            log.warn("Pago inválido: {}", e.getMessage());
        }
        return payment;
    }
    
    private void validatePayment(Payment payment) {
        // Validar monto
        if (payment.getAmount().compareTo(BigDecimal.valueOf(minAmount)) < 0) {
            throw new PaymentValidationException("El monto es menor al mínimo permitido: " + minAmount);
        }
        if (payment.getAmount().compareTo(BigDecimal.valueOf(maxAmount)) > 0) {
            throw new PaymentValidationException("El monto es mayor al máximo permitido: " + maxAmount);
        }
        
        // Validar moneda
        if (!currencies.contains(payment.getCurrency())) {
            throw new PaymentValidationException("Moneda no soportada: " + payment.getCurrency());
        }
        
        // Validar email
        if (payment.getCustomerEmail() == null || payment.getCustomerEmail().isEmpty()) {
            throw new PaymentValidationException("Email del cliente es requerido");
        }
        if (!isValidEmail(payment.getCustomerEmail())) {
            throw new PaymentValidationException("Formato de email inválido: " + payment.getCustomerEmail());
        }
        
        // Validar tipo de pago
        if (payment.getPaymentType() == null) {
            throw new PaymentValidationException("Tipo de pago es requerido");
        }
        
        // Validar fecha
        if (payment.getPaymentDate() == null) {
            throw new PaymentValidationException("Fecha de pago es requerida");
        }
        
        log.info("Pago validado exitosamente: {}", payment);
    }
    
    private void processValidPayment(Payment payment) {
        // Calcular comisión
        BigDecimal commission = calculateCommission(payment.getAmount());
        payment.setCommission(commission);
        
        // Convertir a USD (placeholder para futura implementación)
        BigDecimal amountInUSD = convertToUSD(payment.getAmount(), payment.getCurrency());
        payment.setAmountInUSD(amountInUSD);
        
        // Marcar como procesado
        payment.setStatus("PROCESSED");
        payment.setValidationStatus("VALID");
    }
    
    private BigDecimal calculateCommission(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(commissionRate))
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal convertToUSD(BigDecimal amount, String currency) {
        // TODO: Implementar conversión real usando API de cambio de divisas
        return amount;
    }
    
    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    private static class PaymentValidationException extends RuntimeException {
        public PaymentValidationException(String message) {
            super(message);
        }
    }
}
