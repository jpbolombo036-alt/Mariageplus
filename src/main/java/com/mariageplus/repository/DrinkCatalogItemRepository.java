package com.mariageplus.repository;

import com.mariageplus.entity.DrinkCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrinkCatalogItemRepository extends JpaRepository<DrinkCatalogItem, Long> {

    /** Catalogue complet, trié (ordre d'affichage puis nom). */
    List<DrinkCatalogItem> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<DrinkCatalogItem> findAllByOrderByDisplayOrderAscNameAsc();

    Optional<DrinkCatalogItem> findByNameIgnoreCase(String name);
}
