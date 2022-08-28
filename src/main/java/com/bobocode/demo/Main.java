package com.bobocode.demo;

import com.bobocode.bibernate.session.Session;
import com.bobocode.bibernate.session.SessionFactory;
import com.bobocode.demo.model.Pet;
import com.bobocode.demo.model.PetType;
import org.postgresql.ds.PGSimpleDataSource;


public class Main {

    private static SessionFactory sessionFactory = initSessionFactory();

    public static void main(String[] args) {

        Session session = sessionFactory.openSession();

        Pet pet = new Pet();

        pet.setName("new name");
        pet.setAge(15);
        pet.setType(2L);
        session.persist(pet);

        Pet pet1 = session.find(Pet.class, 8L);
        System.out.println("pet1 = " + pet1);

        session.remove(pet1);


        session.close();


    }
    private static SessionFactory initSessionFactory() {

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:5432/biber-test");
        dataSource.setUser("postgres");
        dataSource.setPassword("admin");
        return new SessionFactory(dataSource);
    }
}