package com.moro;

import com.moro.models.Item;
import com.moro.models.Person;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.List;

public class App {
    public static void main(String[] args) {
        Configuration configuration = new Configuration().addAnnotatedClass(Person.class).addAnnotatedClass(Item.class);

        SessionFactory sessionFactory = configuration.buildSessionFactory();

        try (sessionFactory) {
            Session session = sessionFactory.getCurrentSession();
            session.beginTransaction();


            Person person = session.get(Person.class,1);
            System.out.println("Получили человека");

            session.getTransaction().commit();
            // После commit сразу вызывается session.close()

            System.out.println("Сессия закончилась (session.close()");

            // Открываем сессию и транзакцию ещё раз
            session = sessionFactory.getCurrentSession(); // Получили новую сессию
            session.beginTransaction();

            System.out.println("Внутри второй транзакции");
            person = (Person)session.merge(person);// Мы связываем person из старой сессии в новую
            // Из detached -> persistent

            List<Item> items = session.createQuery("SELECT i from Item i where i.owner.id=:personId",Item.class)
                            .setParameter("personId", person.getId()).getResultList();


            session.getTransaction().commit();

            System.out.println("Вне второй сессии");
            // Lazy загрузка

            System.out.println(person.getItems());

        }

    }
}
