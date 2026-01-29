package by.moro.ws.productMicroservice.service;

import by.moro.ws.productMicroservice.dto.CreateProductDTO;
import com.moro.ws.core.ProductCreatedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class ProductServiceImpl implements ProductService{
    private KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    @Autowired
    public ProductServiceImpl(KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public String createProduct(CreateProductDTO createProductDTO) throws ExecutionException, InterruptedException {
        // TODO save to DB
        String productId = UUID.randomUUID().toString();
        ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent(productId, createProductDTO.getTitle(), createProductDTO.getPrice(),createProductDTO.getQuantity());

        ProducerRecord<String, ProductCreatedEvent> record = new ProducerRecord<>(
                "product-created-events-topic",
                productId,
                productCreatedEvent
        );
        record.headers().add("messageId", UUID.randomUUID().toString().getBytes());
        // Асинхронный режим
        CompletableFuture<SendResult<String, ProductCreatedEvent>> future = kafkaTemplate
                .send(record);


        future.whenComplete((result, exception) -> {
            if (exception != null) {
                LOGGER.error("Failed to send the message: {}",exception.getMessage());
            } else {
                LOGGER.info("Message successfully sent: {}", result.getRecordMetadata());
            }
            LOGGER.info("Return: {}", productId);
        });

        // Сразу вернем productId.
        return productId;
    }
}
