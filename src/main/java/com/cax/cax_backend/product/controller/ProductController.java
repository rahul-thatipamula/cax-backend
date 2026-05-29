package com.cax.cax_backend.product.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.product.model.Product;
import com.cax.cax_backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.cax.cax_backend.common.service.R2StorageService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final R2StorageService r2StorageService;

    @PostMapping(value = "/upload-images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<String>>> uploadProductImages(
            Authentication auth,
            @RequestParam("files") List<MultipartFile> files) throws java.io.IOException {

        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new com.cax.cax_backend.common.exception.AuthException.UnauthorizedException("User is not authenticated");
        }
        String userId = (String) auth.getPrincipal();

        if (files == null || files.isEmpty()) {
            throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("No files uploaded");
        }

        List<String> urls = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("One of the files is empty");
            }
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("File size exceeds 5MB limit");
            }
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.contains(".")) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid filename");
            }
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            boolean isValidExtension = extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".png");
            
            String contentType = file.getContentType();
            boolean isValidMimeType = false;
            if (contentType == null || contentType.equalsIgnoreCase("application/octet-stream")) {
                isValidMimeType = true;
            } else {
                String mime = contentType.toLowerCase();
                isValidMimeType = mime.equals("image/jpeg") || mime.equals("image/jpg") || mime.equals("image/png");
            }

            if (!isValidExtension || !isValidMimeType) {
                throw new com.cax.cax_backend.common.exception.BusinessException.BadRequestException("Invalid file type. Only JPG, JPEG, and PNG are allowed");
            }
        }

        String folder = "products/" + userId;
        for (MultipartFile file : files) {
            String url = r2StorageService.uploadFile(file, folder);
            urls.add(url);
        }

        return ResponseEntity.ok(ApiResponse.success("Images uploaded successfully", urls));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(Authentication auth, @RequestBody Product body) {
        return ResponseEntity.ok(ApiResponse.created("Product created", productService.createProduct((String) auth.getPrincipal(), body)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<Product>> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(productId)));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<Product>> updateProduct(Authentication auth, @PathVariable String productId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateProduct((String) auth.getPrincipal(), productId, body)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(Authentication auth, @PathVariable String productId) {
        productService.deleteProduct((String) auth.getPrincipal(), productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(productService.getAllProductsForUser((String) auth.getPrincipal())));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<Product>>> getMyProducts(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(productService.getUserProducts((String) auth.getPrincipal())));
    }

    @GetMapping("/my-products")
    public ResponseEntity<ApiResponse<List<Product>>> getMyProductsLegacy(Authentication auth) {
        return getMyProducts(auth);
    }

    @GetMapping("/paginated")
    public ResponseEntity<ApiResponse<Page<Product>>> getProductsPaginated(Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProductsWithPagination((String) auth.getPrincipal(), page, limit)));
    }

    // Admin endpoints
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<Product>>> getAdminProducts(
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            try {
                com.cax.cax_backend.common.enums.ProductEnums.ModerationStatus modStatus = 
                        com.cax.cax_backend.common.enums.ProductEnums.ModerationStatus.fromValue(status);
                return ResponseEntity.ok(ApiResponse.success(productService.getProductsByModerationStatus(modStatus)));
            } catch (Exception e) {
                // fallback
            }
        }
        return ResponseEntity.ok(ApiResponse.success(productService.getAllProductsAdmin()));
    }

    @PostMapping("/{productId}/approve")
    public ResponseEntity<ApiResponse<Product>> approveProduct(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(productService.approveProduct(productId)));
    }

    @PostMapping("/{productId}/reject")
    public ResponseEntity<ApiResponse<Product>> rejectProduct(@PathVariable String productId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success(productService.rejectProduct(productId, body.get("reason"))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getProductStats() {
        return ResponseEntity.ok(ApiResponse.success(productService.getProductStats()));
    }
}
