package com.sparta.haengye_project.product.service;

import com.sparta.haengye_project.product.dto.ProductDetailResponseDto;
import com.sparta.haengye_project.product.dto.ProductRequestDto;
import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.product.repository.ProductRepository;
import com.sparta.haengye_project.user.entity.User;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class ProductService {


    private final ProductRepository productRepository;

    public ProductResponseDto createProduct(ProductRequestDto requestDto, User user) {

        Product product = Product.fromRequestDto(requestDto);
        product.setUser(user);  // 반드시 user를 설정해야 합니다.
        Product savedProduct = productRepository.save(product);
        return savedProduct.toResponseDto();

    }

    public Page<ProductResponseDto> getProductList(int page, int size) {
        Pageable pageable = PageRequest.of(page,size, Sort.by(Sort.Direction.ASC,"id"));
        Page<Product> products = productRepository.findAvailableProducts(pageable);
        return products.map(Product::toResponseDto);
    }

    public ProductDetailResponseDto getProductDetails(Long productId) {

        Product product = productRepository.findByIdWithProductInfo(productId)
                .orElseThrow(()->new IllegalArgumentException("해당 상품이 존재하지 않습니다."+ productId)) ;

        return product.toDetailResponseDto();
    }
}
