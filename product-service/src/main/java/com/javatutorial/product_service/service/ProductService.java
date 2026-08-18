package com.javatutorial.product_service.service;

import com.javatutorial.product_service.exception.CategoryNotFoundException;
import com.javatutorial.product_service.exception.ProductNotFoundException;
import com.javatutorial.product_service.model.Category;
import com.javatutorial.product_service.model.Product;
import com.javatutorial.product_service.repository.CategoryRepository;
import com.javatutorial.product_service.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true) // class-level default: every method is a read unless it says otherwise
public class ProductService {

    private static final int MAX_BATCH_SIZE = 100;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Product> findAllByIds(List<Long> ids) {
        // An unbounded id list lets one request pull the whole table. The cap is both a
        // guard and documentation of what the endpoint promises.
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("At most " + MAX_BATCH_SIZE + " ids per request, got " + ids.size());
        }
        return productRepository.findByIdIn(ids);
    }

    @Transactional
    public Product create(String name, BigDecimal price, Long categoryId) {
        return productRepository.save(new Product(name, price, resolveCategory(categoryId)));
    }

    @Transactional
    public Product update(Long id, String name, BigDecimal price, Long categoryId) {
        Product existing = findById(id);
        existing.setName(name);
        existing.setPrice(price);
        existing.setCategory(resolveCategory(categoryId));
        return productRepository.save(existing);
    }

    // the service resolves the category, not the controller: looking things up in the
    // database is business-layer work, the controller only speaks HTTP
    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException((categoryId)));
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
    }
    // One business operation, two writes. Each save() currently commits in its own
    // transaction, so a failure in the second one leaves the first one behind.
    @Transactional // both saves now share one transaction: either both land or neither does
    public Product createCategoryWithProduct(String categoryName, String productName, BigDecimal price) {
        Category category = categoryRepository.save(new Category(categoryName));
        return productRepository.save(new Product(productName, price, category));
    }
}
