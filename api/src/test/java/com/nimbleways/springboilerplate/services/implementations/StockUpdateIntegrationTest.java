package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
public class StockUpdateIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderProcessingService orderProcessingService;

    @Test
    public void testConcurrentStockUpdate() throws InterruptedException {
        // GIVEN
        Product testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setAvailable(9);
        productRepository.save(testProduct);

        Long productId = testProduct.getId();

        // Create a thread pool to simulate concurrent updates
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            tasks.add(() -> {
                // Call the updateStock method (simulate a small delay to increase race condition likelihood)
                orderProcessingService.updateStock(testProduct);
                return null;
            });
        }

        List<Future<Void>> futures = executorService.invokeAll(tasks);

        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        System.out.println("Final stock: " + updatedProduct.getAvailable());
        assertEquals(0, updatedProduct.getAvailable());

    }
}
