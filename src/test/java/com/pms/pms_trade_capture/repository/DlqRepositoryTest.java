package com.pms.pms_trade_capture.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

class DlqRepositoryTest {

    @Test
    void interfaceIsRepositoryAndExtendsJpaRepository() {
        Repository repo = DlqRepository.class.getAnnotation(Repository.class);
        assertNotNull(repo, "DlqRepository should be annotated with @Repository");
        boolean extendsJpa = false;
        for (Class<?> i : DlqRepository.class.getInterfaces()) {
            if (i.getName().contains("JpaRepository")) {
                extendsJpa = true;
            }
        }
        assertTrue(extendsJpa, "DlqRepository should extend JpaRepository");
    }

    @Test
    void canSaveAndRetrieveSignatureExists() throws NoSuchMethodException {
        // We just verify common JpaRepository methods exist on the interface type
        assertNotNull(DlqRepository.class.getMethod("findAll"));
        assertNotNull(DlqRepository.class.getMethod("save", Object.class));
    }

    @Test
    void repositoryHasNoCustomMethods() {
        // Sanity: DlqRepository should not declare extra custom methods (keeps it simple)
        assertEquals(0, DlqRepository.class.getDeclaredMethods().length);
    }
}
