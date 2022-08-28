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

        Pet pet = session.find(Pet.class, 2L);
        System.out.println("pet = " + pet);

        Pet pet2 = session.find(Pet.class, 2L);
        System.out.println("pet = " + pet2);

        System.out.println(pet == pet2);

        PetType petType = session.find(PetType.class, 1L);
        System.out.println("petType = " + petType);

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