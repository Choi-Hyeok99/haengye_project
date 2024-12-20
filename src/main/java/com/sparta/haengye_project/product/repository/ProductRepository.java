package com.sparta.haengye_project.product.repository;

import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.product.entitiy.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product,Long> {


    @Query("SELECT new com.sparta.haengye_project.product.dto.ProductResponseDto(p.id, p.productName, p.stock, pi.price, pi.imagePath, p.startTime, p.endTime) " +
            "FROM Product p " +
            "JOIN ProductInfo pi ON p.id = pi.product.id")
    List<ProductResponseDto> findAllWithProductInfo();

    @Query("SELECT p FROM Product p WHERE p.stock > 0")
    Page<Product> findAvailableProducts(Pageable pageable);



    @Query("SELECT p FROM Product p JOIN FETCH p.productInfo WHERE p.id = :productId")
    Optional<Product> findByIdWithProductInfo(@Param("productId") Long productId);

}
