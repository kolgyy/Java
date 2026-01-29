package com.moro.dao;

import com.moro.models.Book;
import com.moro.models.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;


@Component
public class PersonDAO {
    // Здесь могут находиться специфические запросы к БД с помощью SQL.
}