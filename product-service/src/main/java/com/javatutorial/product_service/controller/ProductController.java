package com.javatutorial.product_service.controller;

import com.javatutorial.product_service.dto.ProductRequest;
import com.javatutorial.product_service.dto.ProductResponse;
import com.javatutorial.product_service.model.Product;
import com.javatutorial.product_service.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController  // @Controller + automatic JSON serialization
@RequestMapping("/api/products") // base path
public class ProductController {
    private final ProductService productService; // constructor injection, same pattern as in the service

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<ProductResponse> getAll(
            // Spring builds the Pageable from ?page=&size=&sort= query params on its own.
            // @PageableDefault applies when the client sends none - without it the default
            // page size is 20 anyway, but stating it makes the contract explicit.
            @PageableDefault(size = 20, sort = "id")
            Pageable pageable
    ) {
        // Page.map() converts the element type while carrying the paging metadata over,
        // so we do not rebuild totalElements/totalPages by hand
        return productService.findAll(pageable)
                // .stream() // Stream API: similar to chaining .map()/.filter() on a JS array
                .map(ProductResponse::from); // method reference -> shorthand for (p) -> ProductResponse.from(p)
        // .toList(); // collects the stream back into a List (Java 16+)
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        // return ProductResponse.from(productService.findById(id)); // the entity no longer leaks out
        return productService.findResponseById(id); // Cache response
    }

    // Separate path on purpose: the paginated collection returns a Page, this returns a
    // plain List. One URL that sometimes returns one shape and sometimes another is worse
    // than two clearly named endpoints.
    @GetMapping("/by-ids")
    public List<ProductResponse> getByIds(@RequestParam List<Long> ids) {
        // Missing ids are simply absent from the response rather than failing the whole
        // request - the caller can see which ones came back and decide what that means.
        return productService.findAllByIds(ids).stream().map(ProductResponse::from).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // correct REST semantics: a new record was created -> 201, not 200
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        // DTO -> entity: we copy the client-supplied fields BY HAND.
        // That way an extra field in the payload (an id, for example) can never reach the entity.
        return ProductResponse.from(productService.create(request.name(), request.price(), request.categoryId()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request.name(), request.price(), request.categoryId())); // id from the URL, data from the body
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // deleted, nothing to return -> 204
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
