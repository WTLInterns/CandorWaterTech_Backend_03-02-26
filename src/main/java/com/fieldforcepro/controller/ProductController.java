package com.fieldforcepro.controller;

import com.fieldforcepro.model.Product;
import com.fieldforcepro.repository.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductRepository productRepository;
    private final Path uploadRoot;

    public ProductController(
            ProductRepository productRepository,
            @Value("${fieldforcepro.products.upload-dir:product-uploads}") String uploadDir
    ) {
        this.productRepository = productRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (Exception ignored) {
        }
    }

    @GetMapping
    @Operation(summary = "List products with optional search")
    public List<Product> listProducts(@RequestParam(value = "search", required = false) String search) {
        List<Product> all = productRepository.findAll();
        if (search == null || search.isBlank()) {
            return all;
        }
        String q = search.toLowerCase();
        return all.stream()
                .filter(p ->
                        (p.getName() != null && p.getName().toLowerCase().contains(q)) ||
                        (p.getSku() != null && p.getSku().toLowerCase().contains(q)))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by id")
    public ResponseEntity<Product> getProduct(@PathVariable("id") Long id) {
        Optional<Product> product = productRepository.findById(id);
        return product.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    public record ProductRequest(String name, BigDecimal price, String description) { }

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<Product> createProduct(@RequestBody ProductRequest request) {
        String sku = request.name() != null ? request.name().toUpperCase().replaceAll("\\s+", "-") : null;
        Product toSave = Product.builder()
                .name(request.name())
                .sku(sku)
                .category("GENERAL")
                .price(request.price())
                .description(request.description())
                .active(true)
                .build();
        Product saved = productRepository.save(toSave);
        return ResponseEntity.created(URI.create("/products/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing product")
    public ResponseEntity<Product> updateProduct(@PathVariable("id") Long id, @RequestBody ProductRequest request) {
        Optional<Product> existingOpt = productRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Product existing = existingOpt.get();
        existing.setName(request.name());
        existing.setPrice(request.price());
        existing.setDescription(request.description());
        Product saved = productRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace product image")
    @Transactional
    public ResponseEntity<Product> uploadProductImage(
            @PathVariable("id") Long id,
            @RequestPart("image") MultipartFile image
    ) {
        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Product> existingOpt = productRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Product product = existingOpt.get();

        try {
            String ext = "";
            String original = image.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            String filename = UUID.randomUUID() + (ext.isEmpty() ? ".jpg" : ext);
            Path target = uploadRoot.resolve(filename);
            Files.copy(image.getInputStream(), target);
            String url = "/products/images/" + filename;
            product.setImageUrl(url);
        } catch (Exception ignored) {
        }

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/images/{filename}")
    @Operation(summary = "Serve stored product image file")
    public ResponseEntity<Resource> serveProductImage(@PathVariable("filename") String filename) throws MalformedURLException {
        Path file = uploadRoot.resolve(filename).normalize();
        if (!Files.exists(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Resource resource = new UrlResource(file.toUri());
        String contentType = "image/jpeg";
        try {
            String probe = Files.probeContentType(file);
            if (probe != null) {
                contentType = probe;
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + filename)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") Long id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        productRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
