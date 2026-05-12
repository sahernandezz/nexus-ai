package com.sahernandezz.nexusai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitMQConfig {

    // ── Exchange names ──────────────────────────────────────────────────────────
    public static final String DOCUMENT_EXCHANGE   = "nexusai.documents";
    public static final String EMBEDDING_EXCHANGE  = "nexusai.embeddings";
    public static final String EXPORT_EXCHANGE     = "nexusai.exports";
    public static final String DLX_EXCHANGE        = "nexusai.dlx";

    // ── Queue names ─────────────────────────────────────────────────────────────
    public static final String DOCUMENT_INGEST_QUEUE  = "nexusai.document.ingest";
    public static final String EMBEDDING_QUEUE        = "nexusai.embedding.process";
    public static final String EXPORT_JOB_QUEUE       = "nexusai.export.job";
    public static final String DLQ_DOCUMENT           = "nexusai.dlq.document";
    public static final String DLQ_EMBEDDING          = "nexusai.dlq.embedding";

    // ── Routing keys ───────────────────────────────────────────────────────────
    public static final String DOCUMENT_INGEST_KEY = "document.ingest";
    public static final String EMBEDDING_KEY       = "embedding.process";
    public static final String EXPORT_JOB_KEY      = "export.job";

    // ─── Dead Letter Exchange ──────────────────────────────────────────────────
    @Bean
    public TopicExchange dlxExchange() {
        return ExchangeBuilder.topicExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlqDocument() {
        return QueueBuilder.durable(DLQ_DOCUMENT).build();
    }

    @Bean
    public Queue dlqEmbedding() {
        return QueueBuilder.durable(DLQ_EMBEDDING).build();
    }

    @Bean
    public Binding dlqDocumentBinding(Queue dlqDocument, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqDocument).to(dlxExchange).with(DOCUMENT_INGEST_KEY);
    }

    @Bean
    public Binding dlqEmbeddingBinding(Queue dlqEmbedding, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqEmbedding).to(dlxExchange).with(EMBEDDING_KEY);
    }

    // ─── Document Ingestion Exchange & Queue ───────────────────────────────────
    @Bean
    public TopicExchange documentExchange() {
        return ExchangeBuilder.topicExchange(DOCUMENT_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue documentIngestQueue() {
        return QueueBuilder.durable(DOCUMENT_INGEST_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DOCUMENT_INGEST_KEY)
                .withArgument("x-message-ttl", 300_000)
                .build();
    }

    @Bean
    public Binding documentIngestBinding(Queue documentIngestQueue, TopicExchange documentExchange) {
        return BindingBuilder.bind(documentIngestQueue).to(documentExchange).with(DOCUMENT_INGEST_KEY);
    }

    // ─── Embedding Exchange & Queue ────────────────────────────────────────────
    @Bean
    public TopicExchange embeddingExchange() {
        return ExchangeBuilder.topicExchange(EMBEDDING_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue embeddingQueue() {
        return QueueBuilder.durable(EMBEDDING_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", EMBEDDING_KEY)
                .build();
    }

    @Bean
    public Binding embeddingBinding(Queue embeddingQueue, TopicExchange embeddingExchange) {
        return BindingBuilder.bind(embeddingQueue).to(embeddingExchange).with(EMBEDDING_KEY);
    }

    // ─── Export Exchange & Queue ───────────────────────────────────────────────
    @Bean
    public TopicExchange exportExchange() {
        return ExchangeBuilder.topicExchange(EXPORT_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue exportJobQueue() {
        return QueueBuilder.durable(EXPORT_JOB_QUEUE).build();
    }

    @Bean
    public Binding exportJobBinding(Queue exportJobQueue, TopicExchange exportExchange) {
        return BindingBuilder.bind(exportJobQueue).to(exportExchange).with(EXPORT_JOB_KEY);
    }

    // ─── Message Converter & Template ─────────────────────────────────────────
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setReplyTimeout(30_000);
        return template;
    }
}

