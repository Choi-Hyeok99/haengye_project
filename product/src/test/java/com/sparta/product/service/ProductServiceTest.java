package com.sparta.product.service;

import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.entitiy.ProductStatus;
import com.sparta.product.entitiy.ProductType;
import com.sparta.product.exception.NotFoundException;
import com.sparta.product.redis.RedisUtility;
import com.sparta.product.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

    class ProductServiceTest {

        @InjectMocks
        private ProductService productService;

        @Mock
        private ProductRepository productRepository;

        @Mock
        private RedisUtility redisUtility;

        @Mock
        private HttpServletRequest request;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this); // Mock 초기화
        }

        @Test
        void createProduct_shouldSaveProductAndReturnResponse() {
            // Given
            ProductRequestDto requestDto = new ProductRequestDto(
                    "Test Product",        // 제품 이름
                    10,                    // 재고 수량
                    1000,                  // 가격
                    "Test Description",    // 상세 설명
                    null,                  // 판매 시작 시간 (null 처리)
                    null,                  // 판매 종료 시간 (null 처리)
                    "/images/test.jpg",    // 이미지 경로
                    "AVAILABLE",           // 초기 상태
                    ProductType.GENERAL.name() // 일반 상품 유형
            );

            // Mock 설정
            when(request.getHeader("X-Claim-sub")).thenReturn("1"); // 사용자 ID 반환
            Product mockProduct = Product.fromRequestDto(requestDto); // DTO -> Entity 변환
            mockProduct.setId(1L); // 저장 후 ID 설정
            when(productRepository.save(any(Product.class))).thenReturn(mockProduct); // 저장 로직 Mock


            // When
            ProductResponseDto result = productService.createProduct(requestDto, request);

            // Then
            // ArgumentCaptor로 RedisUtility의 호출 인자를 캡처
            ArgumentCaptor<String> stockKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> stockValueCaptor = ArgumentCaptor.forClass(Integer.class);

            // verify로 RedisUtility 메서드 호출 검증
            verify(redisUtility).saveToCache(stockKeyCaptor.capture(), stockValueCaptor.capture());

            // 캡처된 값 검증
            assertEquals("product_stock:1", stockKeyCaptor.getValue());
            assertEquals(10, stockValueCaptor.getValue());
        }

        @Test
        void getProductList_shouldReturnProductPage() {
            // Given
            int page = 0;
            int size = 5;

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
            Product mockProduct = new Product();
            mockProduct.setId(1L);
            mockProduct.setProductName("Test Product");
            mockProduct.setStock(10);
            mockProduct.setUserId(1L);
            mockProduct.setProductType(ProductType.GENERAL);
            mockProduct.setStatus(ProductStatus.AVAILABLE);

            Page<Product> mockPage = new PageImpl<>(List.of(mockProduct), pageable, 1); // Mock 데이터 설정
            when(productRepository.findAll(pageable)).thenReturn(mockPage);

            // When
            Page<ProductResponseDto> result = productService.getProductList(page, size);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getTotalElements()); // 총 요소 개수 확인
            assertEquals("Test Product", result.getContent().get(0).getProductName()); // 제품 이름 확인
            verify(productRepository).findAll(pageable); // Mock 호출 확인
        }

        @Test
        void getProductList_shouldThrowNotFoundExceptionWhenEmpty() {
            // Given
            int page = 0;
            int size = 5;
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
            when(productRepository.findAll(pageable)).thenReturn(Page.empty());

            // When & Then
            assertThrows(NotFoundException.class, () -> productService.getProductList(page, size));
            verify(productRepository).findAll(pageable); // Mock 호출 확인
        }

        @Test
        void getProductStock_shouldReturnStockWhenProductExists() {
            // Given
            Long productId = 1L;
            Product mockProduct = new Product();
            mockProduct.setId(productId);
            mockProduct.setStock(100); // Mock 데이터: 재고 설정

            when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

            // When
            int stock = productService.getProductStock(productId);

            // Then
            assertEquals(100, stock); // 재고 값이 100인지 검증
            verify(productRepository).findById(productId); // Repository 호출 검증
        }

        @Test
        void getProductStock_shouldThrowNotFoundExceptionWhenProductDoesNotExist() {
            // Given
            Long productId = 1L;

            when(productRepository.findById(productId)).thenReturn(Optional.empty()); // 상품이 존재하지 않는 경우 설정

            // When & Then
            assertThrows(NotFoundException.class, () -> productService.getProductStock(productId));
            verify(productRepository).findById(productId); // Repository 호출 검증
        }

        @Test
        void updateStockWithDistributedLock_shouldUpdateStockSuccessfully() {
            // Given
            Long productId = 1L;
            int quantity = 10;

            // Mock Product
            Product mockProduct = new Product();
            mockProduct.setId(productId);
            mockProduct.setStock(50); // 기존 재고

            // Redis 락 Mock 설정
            when(redisUtility.acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS)).thenReturn(true);

            // ProductRepository Mock 설정
            when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));
            when(productRepository.save(any(Product.class))).thenReturn(mockProduct);

            // When
            productService.updateStockWithDistributedLock(productId, quantity);

            // Then
            verify(redisUtility).acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS); // 락 획득 확인
            verify(redisUtility).releaseLock("product_stock_lock:" + productId); // 락 해제 확인
            verify(productRepository).findById(productId); // 상품 조회 확인
            verify(productRepository).save(mockProduct); // 재고 저장 확인
            verify(redisUtility).saveToCache("product_stock:" + productId, 60); // Redis 캐시 갱신 확인
            assertEquals(60, mockProduct.getStock()); // 재고가 60으로 업데이트되었는지 확인
        }

        @Test
        void updateStockWithDistributedLock_shouldThrowExceptionWhenLockFails() {
            // Given
            Long productId = 1L;
            int quantity = 10;

            // Redis 락 Mock 설정: 락 획득 실패
            when(redisUtility.acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS)).thenReturn(false);

            // When & Then
            assertThrows(IllegalStateException.class, () -> productService.updateStockWithDistributedLock(productId, quantity));

            // Verify
            verify(redisUtility).acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS); // 락 획득 시도 확인
            verifyNoMoreInteractions(redisUtility); // 락 해제 등 다른 Redis 호출 없음
            verifyNoInteractions(productRepository); // Repository 호출 없음
        }

        @Test
        void updateStockWithDistributedLock_shouldThrowExceptionWhenProductNotFound() {
            // Given
            Long productId = 1L;
            int quantity = 10;

            // Redis 락 Mock 설정
            when(redisUtility.acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS)).thenReturn(true);

            // ProductRepository Mock 설정: 상품이 없을 경우
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(NotFoundException.class, () -> productService.updateStockWithDistributedLock(productId, quantity));

            // Verify
            verify(redisUtility).acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS); // 락 획득 확인
            verify(redisUtility).releaseLock("product_stock_lock:" + productId); // 락 해제 확인
            verify(productRepository).findById(productId); // 상품 조회 확인
            verifyNoMoreInteractions(productRepository); // 저장 호출 없음
        }

        @Test
        void updateStockWithDistributedLock_shouldThrowExceptionWhenStockInsufficient() {
            // Given
            Long productId = 1L;
            int quantity = -60; // 재고 부족 상황

            // Mock Product
            Product mockProduct = new Product();
            mockProduct.setId(productId);
            mockProduct.setStock(50); // 기존 재고

            // Redis 락 Mock 설정
            when(redisUtility.acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS)).thenReturn(true);

            // ProductRepository Mock 설정
            when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> productService.updateStockWithDistributedLock(productId, quantity));

            // Verify
            verify(redisUtility).acquireLock("product_stock_lock:" + productId, 1000, TimeUnit.MILLISECONDS); // 락 획득 확인
            verify(redisUtility).releaseLock("product_stock_lock:" + productId); // 락 해제 확인
            verify(productRepository).findById(productId); // 상품 조회 확인
            verifyNoMoreInteractions(productRepository); // 저장 호출 없음
        }




    }