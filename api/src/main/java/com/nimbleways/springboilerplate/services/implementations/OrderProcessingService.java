package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

@Service
@AllArgsConstructor
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        Set<Product> products = order.getItems();
        for (Product product : products) {
            processProduct(product);
        }
    }

    private void processProduct(Product product) {
        switch (product.getType()) {
            case NORMAL:
                handleNormalProduct(product);
                break;
            case SEASONAL:
                handleSeasonalProduct(product);
                break;
            case EXPIRABLE:
                handleExpirableProduct(product);
                break;
            default:
                throw new IllegalStateException("Unknown product type: " + product.getType());
        }
    }

    private void handleNormalProduct(Product product) {
        if (!updateStock(product)) {
            if (product.getLeadTime() > 0) {
                productService.notifyDelay(product.getLeadTime(), product);
            }
        }
    }

    private void handleSeasonalProduct(Product product) {
        LocalDate now = LocalDate.now();
        if (now.isAfter(product.getSeasonStartDate()) &&
                now.isBefore(product.getSeasonEndDate()) &&
                updateStock(product)) {
            return;
        }
        productService.handleSeasonalProduct(product);
    }

    private void handleExpirableProduct(Product product) {
        if (product.getExpiryDate().isAfter(LocalDate.now()) && updateStock(product)) {
            return;
        }
        productService.handleExpiredProduct(product);
    }

    protected boolean updateStock(Product product) {
        int updatedRows = productRepository.decrementStockIfAvailable(product.getId());
        return updatedRows > 0;
    }
}