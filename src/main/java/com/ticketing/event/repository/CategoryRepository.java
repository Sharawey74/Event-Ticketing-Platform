package com.ticketing.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.event.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
