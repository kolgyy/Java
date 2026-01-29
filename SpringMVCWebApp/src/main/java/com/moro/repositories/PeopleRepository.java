package com.moro.repositories;

import com.moro.models.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PeopleRepository extends JpaRepository<Person, Integer> {
    // Ищем по имени
    List<Person> findByName(String name);

    // Ищем по имени и сортируем по возрасту
    List<Person> findByNameOrderByAge(String name);

    List<Person> findByEmail(String email);

    // Передаём строку, которая является началом имени
    List<Person> findByNameStartingWith(String startingWith);

    // Вернем всех людей, у которых совпало либо имя, либо почта
    List<Person> findByNameOrEmail(String name, String email);

}
