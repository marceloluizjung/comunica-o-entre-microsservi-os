package br.com.cursoudemy.productapi.modules.category.model;

import br.com.cursoudemy.productapi.modules.category.dto.CategoryRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import javax.persistence.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer id;

    @Column(nullable = false)
    private String description;

    public static Category of(CategoryRequest request) {
        Category category = new Category();
        BeanUtils.copyProperties(request, category);
        return category;
    }
}
