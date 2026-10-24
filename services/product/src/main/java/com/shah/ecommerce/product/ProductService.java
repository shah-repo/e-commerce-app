package com.shah.ecommerce.product;

import com.shah.ecommerce.exception.ProductPurchaseException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Product not found with ID: %d", productId)
                ));
    }

    public List<ProductResponse> findAll() {
        return repository.findAll().stream()
                .map(mapper::toProductResponse)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ProductPurchaseResponse> purchaseProduct(List<ProductPurchaseRequest> request) {
        Map<Integer, Double> mergedQuantities = request.stream()
                .collect(Collectors.toMap(
                        ProductPurchaseRequest::productId,
                        ProductPurchaseRequest::quantity,
                        Double::sum
                ));
        var productIds = mergedQuantities.keySet().stream().sorted().toList();
        var storedProducts = repository.findAllByIdInOrderById(productIds);
        if (storedProducts.size() != productIds.size()) {
            throw new ProductPurchaseException("One or more requested products do not exist.");
        }

        var purchasedProducts = new ArrayList<ProductPurchaseResponse>();
        for (var storedProduct : storedProducts) {
            double requestedQuantity = mergedQuantities.get(storedProduct.getId());
            if (storedProduct.getAvailableQuantity() < requestedQuantity) {
                throw new ProductPurchaseException(String.format(
                        "Insufficient stock quantity for product '%s' (ID: %d). Available: %.0f, Requested: %.0f.",
                        storedProduct.getName(),
                        storedProduct.getId(),
                        storedProduct.getAvailableQuantity(),
                        requestedQuantity
                ));
            }
            var newAvailableQuantity = storedProduct.getAvailableQuantity() - requestedQuantity;
            storedProduct.setAvailableQuantity(newAvailableQuantity);
            repository.save(storedProduct);

            purchasedProducts.add(mapper.toProductPurchaseResponse(storedProduct, requestedQuantity));
        }

        return purchasedProducts;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollbackPurchase(List<ProductPurchaseRequest> request) {
        if (request == null || request.isEmpty()) {
            return;
        }
        Map<Integer, Double> mergedQuantities = request.stream()
                .collect(Collectors.toMap(
                        ProductPurchaseRequest::productId,
                        ProductPurchaseRequest::quantity,
                        Double::sum
                ));
        var productIds = mergedQuantities.keySet().stream().sorted().toList();
        var storedProducts = repository.findAllByIdInOrderById(productIds);
        for (var storedProduct : storedProducts) {
            Double qty = mergedQuantities.get(storedProduct.getId());
            if (qty != null && qty > 0) {
                storedProduct.setAvailableQuantity(storedProduct.getAvailableQuantity() + qty);
                repository.save(storedProduct);
            }
        }
    }
}
