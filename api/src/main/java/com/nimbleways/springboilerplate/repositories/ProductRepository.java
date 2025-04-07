package com.nimbleways.springboilerplate.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nimbleways.springboilerplate.entities.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findById(Long productId);

    Optional<Product> findFirstByName(String name);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.available = p.available - 1 WHERE p.id = :id AND p.available > 0")
    int decrementStockIfAvailable(@Param("id") Long id);
}
