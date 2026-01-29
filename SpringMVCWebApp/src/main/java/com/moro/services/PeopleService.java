package com.moro.services;

import com.moro.models.Mood;
import com.moro.models.Person;
import com.moro.repositories.PeopleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PeopleService {

    private final PeopleRepository peopleRepository;

    @Autowired
    public PeopleService(PeopleRepository peopleRepository) {
        this.peopleRepository = peopleRepository;
    }
    // По умолчанию помечены @Transactional(readOnly=true)
    public List<Person> findAll() {
        // Возвращает все сущности из таблицы
        return peopleRepository.findAll();
    }
    public Person findOne(int id) {
        Optional<Person> foundPerson = peopleRepository.findById(id);
        return foundPerson.orElse(null); // Возвращаем либо человека, либо Null
    }
    @Transactional
    public void save(Person person) {
        person.setMood(Mood.CALM);
        person.setCreatedAt(new Date());
        peopleRepository.save(person);
    }
    @Transactional
    public void update(int id, Person updatedPerson) {
        updatedPerson.setId(id);
        // Метод увидит, что передаётся человек с ID, которое уже есть в таблице,
        // поэтому он просто обновит значения у существующего человека
        peopleRepository.save(updatedPerson);
    }
    @Transactional
    public void delete(int id) {
        peopleRepository.deleteById(id);
    }

    public void test() {
        System.out.println("Testing here with debug. Inside Hibernate Transaction");
    }
}
