package com.example.batch.config;

import com.example.batch.model.Payment;
import com.example.batch.processor.PaymentItemProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

@Slf4j
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Value("${payment.commission.rate}")
    private double commissionRate;

    private List<Payment> invalidPayments = new ArrayList<>();

    @Bean
    public FlatFileItemReader<Payment> reader() {
        log.info("Configurando el lector de archivos...");
        FlatFileItemReader<Payment> reader = new FlatFileItemReader<>();
        reader.setResource(new ClassPathResource("input/payments.txt"));
        reader.setLinesToSkip(1);

        DefaultLineMapper<Payment> lineMapper = new DefaultLineMapper<>();
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("id", "amount", "currency", "status", "paymentDate", "paymentType", "customerName", "customerEmail");

        BeanWrapperFieldSetMapper<Payment> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(Payment.class);
        
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(new StringToLocalDateTimeConverter());
        fieldSetMapper.setConversionService(conversionService);

        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);
        reader.setLineMapper(lineMapper);

        return reader;
    }

    @Bean
    public ItemReader<Payment> invalidPaymentsReader() {
        return () -> {
            if (!invalidPayments.isEmpty()) {
                return invalidPayments.remove(0);
            }
            return null;
        };
    }

    private static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.trim().isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(source, formatter);
            } catch (Exception e) {
                return null;
            }
        }
    }

    @Bean
    public PaymentItemProcessor processor() {
        log.info("Configurando el procesador de pagos...");
        PaymentItemProcessor processor = new PaymentItemProcessor();
        processor.setCommissionRate(commissionRate);
        processor.setMinAmount(10.0);
        processor.setMaxAmount(10000.0);
        processor.setSupportedCurrencies("USD,EUR,GBP,JPY");
        return processor;
    }

    @Bean
    public ItemProcessor<Payment, Payment> validPaymentProcessor() {
        return payment -> {
            if ("PROCESSED".equals(payment.getStatus())) {
                return payment;
            }
            invalidPayments.add(payment);
            return null;
        };
    }

    @Bean
    public FlatFileItemWriter<Payment> validPaymentsWriter() {
        log.info("Configurando el escritor de pagos válidos...");
        FlatFileItemWriter<Payment> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource("src/main/resources/output/processed_payments.txt"));
        writer.setShouldDeleteIfExists(true);
        writer.setAppendAllowed(false);
        
        DelimitedLineAggregator<Payment> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(",");
        
        BeanWrapperFieldExtractor<Payment> fieldExtractor = new BeanWrapperFieldExtractor<>();
        fieldExtractor.setNames(new String[] {
            "id", "amount", "currency", "status", "paymentDate", 
            "paymentType", "customerName", "customerEmail",
            "amountInUSD", "commission", "validationStatus"
        });
        
        lineAggregator.setFieldExtractor(fieldExtractor);
        writer.setLineAggregator(lineAggregator);
        
        return writer;
    }

    @Bean
    public ItemWriter<Payment> invalidPaymentsCollector() {
        return items -> {
            for (Payment payment : items) {
                if (!"PROCESSED".equals(payment.getStatus())) {
                    invalidPayments.add(payment);
                }
            }
        };
    }

    @Bean
    public CompositeItemWriter<Payment> validPaymentWriter() {
        CompositeItemWriter<Payment> writer = new CompositeItemWriter<>();
        writer.setDelegates(List.of(validPaymentsWriter(), invalidPaymentsCollector()));
        return writer;
    }

    @Bean
    public FlatFileItemWriter<Payment> reportWriter() {
        log.info("Configurando el escritor del reporte...");
        FlatFileItemWriter<Payment> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource("src/main/resources/output/payment_report.txt"));
        writer.setShouldDeleteIfExists(true);
        writer.setAppendAllowed(false);
        
        DelimitedLineAggregator<Payment> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter("|");
        
        BeanWrapperFieldExtractor<Payment> fieldExtractor = new BeanWrapperFieldExtractor<>();
        fieldExtractor.setNames(new String[] {
            "id", "amount", "currency", "amountInUSD", 
            "commission", "paymentType", "status"
        });
        
        lineAggregator.setFieldExtractor(fieldExtractor);
        writer.setLineAggregator(lineAggregator);
        
        return writer;
    }

    @Bean
    public FlatFileItemWriter<Payment> rejectedWriter() {
        log.info("Configurando el escritor de pagos rechazados...");
        FlatFileItemWriter<Payment> writer = new FlatFileItemWriter<>();
        writer.setResource(new FileSystemResource("src/main/resources/output/rejected_payments.txt"));
        writer.setShouldDeleteIfExists(true);
        writer.setAppendAllowed(false);
        
        DelimitedLineAggregator<Payment> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(",");
        
        BeanWrapperFieldExtractor<Payment> fieldExtractor = new BeanWrapperFieldExtractor<>();
        fieldExtractor.setNames(new String[] {
            "id", "amount", "currency", "status", "paymentDate", 
            "paymentType", "customerName", "customerEmail", "errorMessage"
        });
        
        lineAggregator.setFieldExtractor(fieldExtractor);
        writer.setLineAggregator(lineAggregator);
        
        return writer;
    }

    @Bean
    public ItemWriter<Payment> step1Writer() {
        FlatFileItemWriter<Payment> writer = validPaymentsWriter();
        writer.open(new ExecutionContext());
        
        return items -> {
            log.info("Escribiendo {} pagos en el paso 1", items.size());
            List<Payment> validPayments = new ArrayList<>();
            
            for (Payment payment : items) {
                if ("PROCESSED".equals(payment.getStatus())) {
                    log.info("Agregando pago procesado: {}", payment);
                    validPayments.add(payment);
                } else {
                    log.info("Agregando pago inválido a la lista: {}", payment);
                    invalidPayments.add(payment);
                }
            }
            
            if (!validPayments.isEmpty()) {
                writer.write(validPayments);
            }
        };
    }

    @Bean
    public Step step1() {
        log.info("Configurando el paso 1...");
        return stepBuilderFactory.get("step1")
                .<Payment, Payment>chunk(10)
                .reader(reader())
                .processor(processor())
                .writer(step1Writer())
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(StepExecution stepExecution) {
                        // No necesitamos hacer nada antes del paso
                    }

                    @Override
                    public ExitStatus afterStep(StepExecution stepExecution) {
                        validPaymentsWriter().close();
                        return ExitStatus.COMPLETED;
                    }
                })
                .build();
    }

    @Bean
    public Step step2() {
        log.info("Configurando el paso 2...");
        return stepBuilderFactory.get("step2")
                .<Payment, Payment>chunk(10)
                .reader(reader())
                .processor(processor())
                .writer(reportWriter())
                .build();
    }

    @Bean
    public Step step3() {
        log.info("Configurando el paso 3 para pagos rechazados...");
        return stepBuilderFactory.get("step3")
                .<Payment, Payment>chunk(10)
                .reader(invalidPaymentsReader())
                .writer(rejectedWriter())
                .build();
    }

    @Bean
    public Job importPaymentsJob() {
        log.info("Configurando el job de importación de pagos...");
        return jobBuilderFactory.get("importPaymentsJob")
                .incrementer(new RunIdIncrementer())
                .start(step1())
                .next(step2())
                .next(step3())
                .build();
    }
}
