package com.mariageplus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParserTest {

    @Test
    void parseLine_handlesQuotedComma() {
        var fields = CsvParser.parseLine("Jean,\"Kinshasa, Gombe\",VIP", ',');
        assertEquals("Jean", fields.get(0));
        assertEquals("Kinshasa, Gombe", fields.get(1));
        assertEquals("VIP", fields.get(2));
    }

    @Test
    void detectDelimiter_prefersSemicolonWhenMoreFrequent() {
        assertEquals(';', CsvParser.detectDelimiter("firstName;lastName;email"));
        assertEquals(',', CsvParser.detectDelimiter("firstName,lastName,email"));
    }

    @Test
    void stripBom_removesUtf8Bom() {
        assertEquals("firstName", CsvParser.stripBom("\uFEFFfirstName"));
    }
}
