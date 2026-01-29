package by.moro.ws.productMicroservice.service;

import by.moro.ws.productMicroservice.dto.CreateProductDTO;

import java.util.concurrent.ExecutionException;

public interface ProductService {
    String createProduct(CreateProductDTO createProductDTO) throws ExecutionException, InterruptedException;
}
