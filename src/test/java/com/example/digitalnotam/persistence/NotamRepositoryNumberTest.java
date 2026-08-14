package com.example.digitalnotam.persistence;

import com.example.digitalnotam.domain.Notam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotamRepositoryNumberTest {
    @Test
    void draftMayKeepOrChangeItsOwnNumberButCannotUseAnotherNotamsNumber() {
        NotamRepository repository = new NotamRepository();
        repository.seedIfEmpty();
        Notam draft = repository.all().iterator().next();
        String year = draft.number().substring(draft.number().indexOf('/'));

        assertEquals(draft.number(),
                repository.assignNumberForUpdate(draft.id(), "A", "1001"));
        assertEquals("C0123" + year,
                repository.assignNumberForUpdate(draft.id(), "C", "123"));
        assertThrows(IllegalStateException.class,
                () -> repository.assignNumberForUpdate("another-draft", "A", "1001"));
    }
}
