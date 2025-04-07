package com.nimbleways.springboilerplate.services.implementations;

import com.nimbleways.springboilerplate.entities.Order;
import com.nimbleways.springboilerplate.entities.Product;
import com.nimbleways.springboilerplate.entities.enume.ProductType;
import com.nimbleways.springboilerplate.repositories.OrderRepository;
import com.nimbleways.springboilerplate.repositories.ProductRepository;
import com.nimbleways.springboilerplate.utils.Annotations.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@UnitTest
public class OrderProcessingTests {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductService productService;
    @InjectMocks
    private OrderProcessingService orderProcessingService;

    @Test
    public void testProcessOrder_orderNotFound() {
        // GIVEN
        Long wrongOrderId = 999L;
        Mockito.when(orderRepository.findById(wrongOrderId)).thenReturn(Optional.empty());

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderProcessingService.processOrder(wrongOrderId);
        });

        // Optionally, you can assert the exception message if expected
        assertEquals("Order not found: " + wrongOrderId, exception.getMessage());
    }

    @Test
    public void testProcessProduct_normalProduct() {
        // GIVEN
        Product mockedProduct = Mockito.mock(Product.class);
        Long productId = 123L;
        Mockito.when(mockedProduct.getId()).thenReturn(productId);
        Mockito.when(mockedProduct.getType()).thenReturn(ProductType.NORMAL);
        Mockito.when(mockedProduct.getLeadTime()).thenReturn(5);
        Order order = new Order();
        Set<Product> items = new HashSet<>();
        items.add(mockedProduct);
        order.setItems(items);
        Mockito.when(orderRepository.findById(productId)).thenReturn(Optional.of(order));

        Mockito.when(productRepository.decrementStockIfAvailable(productId)).thenReturn(1);

        // WHEN
        orderProcessingService.processOrder(productId);

        // Verify that notifyDelay was NOT called as stock update is successful
        Mockito.verify(productService, Mockito.times(0)).notifyDelay(Mockito.anyInt(), Mockito.any(Product.class));
    }

    @Test
    public void testProcessProduct_seasonalProduct_inSeason_stockUpdated() {
        // GIVEN
        Product mockedProduct = Mockito.mock(Product.class);
        Long productId = 123L;
        LocalDate seasonStartDate = LocalDate.now().minusDays(5);  // 5 days ago
        LocalDate seasonEndDate = LocalDate.now().plusDays(5);      // 5 days in the future

        Mockito.when(mockedProduct.getId()).thenReturn(productId);
        Mockito.when(mockedProduct.getType()).thenReturn(ProductType.SEASONAL);
        Mockito.when(mockedProduct.getSeasonStartDate()).thenReturn(seasonStartDate);
        Mockito.when(mockedProduct.getSeasonEndDate()).thenReturn(seasonEndDate);

        Order order = new Order();
        Set<Product> items = new HashSet<>();
        items.add(mockedProduct);
        order.setItems(items);
        Mockito.when(orderRepository.findById(productId)).thenReturn(Optional.of(order));
        Mockito.when(productRepository.decrementStockIfAvailable(productId)).thenReturn(1);

        // WHEN
        orderProcessingService.processOrder(productId);

        // THEN
        Mockito.verify(productRepository, Mockito.times(1)).decrementStockIfAvailable(productId);
        Mockito.verify(productService, Mockito.times(0)).handleSeasonalProduct(mockedProduct);
    }

    @Test
    public void testProcessProduct_seasonalProduct_outOfSeason_stockNotUpdated() {
        // GIVEN
        Product mockedProduct = Mockito.mock(Product.class);
        Long productId = 123L;
        LocalDate seasonStartDate = LocalDate.now().minusDays(10);  // 10 days ago
        LocalDate seasonEndDate = LocalDate.now().minusDays(5);      // 5 days ago

        Mockito.when(mockedProduct.getId()).thenReturn(productId);
        Mockito.when(mockedProduct.getType()).thenReturn(ProductType.SEASONAL);
        Mockito.when(mockedProduct.getSeasonStartDate()).thenReturn(seasonStartDate);
        Mockito.when(mockedProduct.getSeasonEndDate()).thenReturn(seasonEndDate);

        Order order = new Order();
        Set<Product> items = new HashSet<>();
        items.add(mockedProduct);
        order.setItems(items);
        Mockito.when(orderRepository.findById(productId)).thenReturn(Optional.of(order));

        Mockito.when(productRepository.decrementStockIfAvailable(productId)).thenReturn(0);

        // WHEN
        orderProcessingService.processOrder(productId);

        // THEN
        Mockito.verify(productService, Mockito.times(1)).handleSeasonalProduct(mockedProduct);
    }

    @Test
    public void testProcessProduct_expirableProduct_notExpired_stockUpdated() {
        // GIVEN
        Product mockedProduct = Mockito.mock(Product.class);
        Long productId = 123L;
        LocalDate expiryDate = LocalDate.now().plusDays(5);  // 5 days from now (not expired)

        Mockito.when(mockedProduct.getId()).thenReturn(productId);
        Mockito.when(mockedProduct.getType()).thenReturn(ProductType.EXPIRABLE);
        Mockito.when(mockedProduct.getExpiryDate()).thenReturn(expiryDate);

        Order order = new Order();
        Set<Product> items = new HashSet<>();
        items.add(mockedProduct);
        order.setItems(items);

        Mockito.when(orderRepository.findById(productId)).thenReturn(Optional.of(order));
        Mockito.when(productRepository.decrementStockIfAvailable(productId)).thenReturn(1);  // Stock updated successfully

        // WHEN
        orderProcessingService.processOrder(productId);

        // THEN
        Mockito.verify(productRepository, Mockito.times(1)).decrementStockIfAvailable(productId);
        Mockito.verify(productService, Mockito.times(0)).handleExpiredProduct(mockedProduct);
    }

    @Test
    public void testProcessProduct_expirableProduct_expired() {
        // GIVEN
        Product mockedProduct = Mockito.mock(Product.class);
        Long productId = 123L;
        LocalDate expiryDate = LocalDate.now().minusDays(5);  // 5 days ago (expired)

        Mockito.when(mockedProduct.getId()).thenReturn(productId);
        Mockito.when(mockedProduct.getType()).thenReturn(ProductType.EXPIRABLE);
        Mockito.when(mockedProduct.getExpiryDate()).thenReturn(expiryDate);

        Order order = new Order();
        Set<Product> items = new HashSet<>();
        items.add(mockedProduct);
        order.setItems(items);

        Mockito.when(orderRepository.findById(productId)).thenReturn(Optional.of(order));

        Mockito.when(productRepository.decrementStockIfAvailable(productId)).thenReturn(0);  // No stock available

        // WHEN
        orderProcessingService.processOrder(productId);

        // THEN
        Mockito.verify(productService, Mockito.times(1)).handleExpiredProduct(mockedProduct);
    }

}