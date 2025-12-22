package com.shah.ecommerce.product;

import com.shah.ecommerce.exception.ProductPurchaseException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    public Integer createProduct(ProductRequest request) {
        var product = mapper.toProduct(request);
        return repository.save(product).getId();
    }

    public ProductResponse findById(Integer productId) {
        return repository.findById(productId)
                .map(mapper::toProductResponse)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with the given ID:: %s" + productId));
    }

    public List<ProductResponse> findAll() {
        return repository.findAll().stream()
                .map(mapper::toProductResponse)
                .collect(Collectors.toList());
    }

    public List<ProductPurchaseResponse> purchaseProduct(List<ProductPurchaseRequest> request) {
        var productIds = request.stream().map(ProductPurchaseRequest::productId).toList();
        var storedProducts = repository.findAllByIdInOrderById(productIds);
        if (storedProducts.size() != productIds.size()){
            throw new ProductPurchaseException("One or more products doesn't exists");
        }

        var requestedProducts = request
                .stream().
                sorted(Comparator.comparing(ProductPurchaseRequest::productId))
                .toList();
        var purchasedProducts = new ArrayList<ProductPurchaseResponse>();
        for (int i =0; i<storedProducts.size();i++){
            var storedProduct = storedProducts.get(i);
            var requestedProduct = requestedProducts.get(i);
            if (storedProduct.getAvailableQuantity() < requestedProduct.quantity()){
                throw new ProductPurchaseException("Insufficient stock quantity for product with ID:: %s" + storedProduct.getId());
            }
            var newAvailableQuantity = storedProduct.getAvailableQuantity() - requestedProduct.quantity();
            storedProduct.setAvailableQuantity(newAvailableQuantity);
            repository.save(storedProduct);

            purchasedProducts.add(mapper.toProductPurchaseResponse(storedProduct, requestedProduct.quantity()));
        }

        return purchasedProducts;
    }
}
