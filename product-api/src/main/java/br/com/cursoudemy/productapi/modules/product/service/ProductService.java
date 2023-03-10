package br.com.cursoudemy.productapi.modules.product.service;

import br.com.cursoudemy.productapi.config.exception.SuccessResponse;
import br.com.cursoudemy.productapi.config.exception.ValidationException;
import br.com.cursoudemy.productapi.modules.category.model.Category;
import br.com.cursoudemy.productapi.modules.category.service.CategoryService;
import br.com.cursoudemy.productapi.modules.product.dto.*;
import br.com.cursoudemy.productapi.modules.product.model.Product;
import br.com.cursoudemy.productapi.modules.product.repository.ProductRepository;
import br.com.cursoudemy.productapi.modules.sales.client.SalesClient;
import br.com.cursoudemy.productapi.modules.sales.dto.SalesConfirmationDTO;
import br.com.cursoudemy.productapi.modules.sales.dto.SalesProductResponse;
import br.com.cursoudemy.productapi.modules.sales.enums.SalesStatus;
import br.com.cursoudemy.productapi.modules.sales.rabbitmq.SalesConfirmationSender;
import br.com.cursoudemy.productapi.modules.supplier.model.Supplier;
import br.com.cursoudemy.productapi.modules.supplier.service.SupplierService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.type.IntegerType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SalesConfirmationSender salesConfirmationSender;

    @Autowired
    private SalesClient salesClient;

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream().map(ProductResponse::of).collect(Collectors.toList());
    }

    public List<ProductResponse> findByName(String name) {
        if (name == null || name.isEmpty()) throw new ValidationException("The product name must be informed.");
        return productRepository.findByNameIgnoreCaseContaining(name).stream().map(ProductResponse::of).collect(Collectors.toList());
    }

    public List<ProductResponse> findBySupplierId(Integer supplierId) {
        if (supplierId == null) throw new ValidationException("The supplier id must be informed.");
        return productRepository.findBySupplierId(supplierId).stream().map(ProductResponse::of).collect(Collectors.toList());
    }

    public List<ProductResponse> findByCategoryId(Integer categoryId) {
        if (categoryId == null) throw new ValidationException("The category id must be informed.");
        return productRepository.findByCategoryId(categoryId).stream().map(ProductResponse::of).collect(Collectors.toList());
    }

    public ProductResponse findByIdResponse(Integer id) {
        return ProductResponse.of(findById(id));
    }

    public Product findById(Integer id) {
        if (id == null) throw new ValidationException("The product id must be informed.");
        return productRepository.findById(id).orElseThrow(() -> new ValidationException("Theres no product for the given id."));
    }

    public SuccessResponse delete(Integer id) {
        validateInformedId(id);
        if (!productRepository.existsById(id)) {
            throw new ValidationException("The product does not exists.");
        }
        productRepository.deleteById(id);
        return SuccessResponse.create("The product was deleted.");
    }

    public ProductResponse save(ProductRequest request) {
        this.validateProductDataInformed(request);
        this.validateCategoryAndSupplierIdInformed(request);
        Supplier supplier = supplierService.findById(request.getSupplierId());
        Category category = categoryService.findById(request.getCategoryId());
        Product product = productRepository.save(Product.of(request, supplier, category));
        return ProductResponse.of(product);
    }

    public ProductResponse update(ProductRequest request, Integer id) {
        validateProductDataInformed(request);
        validateInformedId(id);
        validateCategoryAndSupplierIdInformed(request);
        var category = categoryService.findById(request.getCategoryId());
        var supplier = supplierService.findById(request.getSupplierId());
        var product = Product.of(request, supplier, category);
        product.setId(id);
        productRepository.save(product);
        return ProductResponse.of(product);
    }

    public void validateProductDataInformed(ProductRequest request) {
        if (ObjectUtils.isEmpty(request.getName())) {
            throw new ValidationException("The product name was not informed.");
        }

        if (ObjectUtils.isEmpty(request.getQuantityAvailable())) {
            throw new ValidationException("The product quantity was not informed.");
        }

        if (request.getQuantityAvailable() <= IntegerType.ZERO) {
            throw new ValidationException("The quantity should not be less or equal to zer.");
        }
    }

    public void validateCategoryAndSupplierIdInformed(ProductRequest request) {
        if (ObjectUtils.isEmpty(request.getCategoryId())) {
            throw new ValidationException("The category id was not informed.");
        }
        if (ObjectUtils.isEmpty(request.getSupplierId())) {
            throw new ValidationException("The supplier id was not informed.");
        }
    }

    private void validateInformedId(Integer id) {
        if (id == null) {
            throw new ValidationException("The supplier ID must be informed.");
        }
    }

    public void updateProductStock(ProductStockDTO product) {
        try {
            validateStockUpdateData(product);
            updateStock(product);
        } catch (Exception ex) {
            log.error("Error while trying to update stock for message with : {}", ex.getMessage(), ex);
            SalesConfirmationDTO rejectedMessage = new SalesConfirmationDTO(product.getSalesId(), SalesStatus.REJECTED);
            salesConfirmationSender.sendSalesConfirmationMessage(rejectedMessage);
        }
    }

    @Transactional
    private void updateStock(ProductStockDTO product) {
        ArrayList<Product> productsForUpdate = new ArrayList();
        product.getProducts().forEach(salesProduct -> {
            Product existingProduct = findById(salesProduct.getProductId());
            validateQuantityInStock(salesProduct, existingProduct);
            existingProduct.updateStock(salesProduct.getQuantity());
            productsForUpdate.add(existingProduct);
        });
        if (!productsForUpdate.isEmpty()) {
            productRepository.saveAll(productsForUpdate);
            SalesConfirmationDTO approvedMessage = new SalesConfirmationDTO(product.getSalesId(), SalesStatus.APPROVED);
            salesConfirmationSender.sendSalesConfirmationMessage(approvedMessage);
        }
    }

    private void validateQuantityInStock(ProductQuantityDTO salesProduct, Product existingProduct) {
        if (salesProduct.getQuantity() > existingProduct.getQuantityAvailable())
            throw new ValidationException(String.format("The product %s is out of stock", existingProduct.getId()));
    }

    private void validateStockUpdateData(ProductStockDTO product) {
        if (product == null || product.getSalesId().isEmpty())
            throw new ValidationException("The product data and the sales ID must be informed");
        if (product.getProducts().isEmpty()) throw new ValidationException("The sales products must be informed.");
        product.getProducts().forEach(salesProduct -> {
            if (salesProduct.getQuantity() == null || salesProduct.getProductId() == null)
                throw new ValidationException("The productID and the quantity must be informed.");
        });
    }

    public ProductSalesResponse findProductSales(Integer id) {
        Product product = findById(id);
        try {
            SalesProductResponse sales = salesClient.findSalesByProductId(id).orElseThrow(() -> new ValidationException("The sales was not found by this product."));
            return ProductSalesResponse.of(product, sales.getSalesIds());
        } catch (Exception ex) {
            throw new ValidationException("There was an error trying to get the product's sales");
        }
    }

    public SuccessResponse checkProductsStock(ProductCheckStockRequest request) {
        if (request == null || request.getProducts().isEmpty())
            throw new ValidationException("The request data products must be informet.");
        request
                .getProducts()
                .forEach(this::validateStock);
        return SuccessResponse.create("The stock is ok!");
    }

    private void validateStock(ProductQuantityDTO productQuantity) {
        if (productQuantity.getProductId() == null || productQuantity.getQuantity() == null)
            throw new ValidationException("Product ID and quantity must be informed");
        Product product = findById(productQuantity.getProductId());
        if (productQuantity.getQuantity() > product.getQuantityAvailable())
            throw new ValidationException(String.format("The product %s is out of stock.", product.getId()));
    }

}
