package by.moro.ws.productMicroservice.controller;

import by.moro.ws.productMicroservice.dto.CreateProductDTO;
import by.moro.ws.productMicroservice.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/product")
public class ProductController {
    private ProductService productService;
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping()
    public ResponseEntity<Object> createProduct(@RequestBody CreateProductDTO createProductDTO) {
        String productID = null;
        try {
            productID = productService.createProduct(createProductDTO);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorMessage(e.getMessage(), LocalDateTime.now()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(productID);
    }
}
