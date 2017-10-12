package uk.bl.wa.util;
/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 */

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class NormalisationTest {

    @Test
    public void testURLNormalisation() {
        final String[][] TESTS = new String[][]{
                // input, ambiguous, unambiguous
                {"http://example.com",  "http://example.com/", "http://example.com/"},
                {"http://example.com/", "http://example.com/", "http://example.com/"},
                {"https://example.com", "http://example.com/", "http://example.com/"},
                {"https://example.com", "http://example.com/", "http://example.com/"},
                {"/foo",                "/foo",                "/foo"},
                {"/foo/",               "/foo",                "/foo"},
                {"/%2A",                "/%2a",                "/*"},
                {"/%2a",                "/%2a",                "/*"},
                {"/%2a*",               "/%2a*",                "/**"},
                {"/æblegrød",           "/æblegrød",           "/æblegrød"},
                {"%C3%A6blegr%C3%B8d",  "æblegrød",            "æblegrød"},
                {"/æblegrød og øl",     "/æblegrød%20og%20øl", "/æblegrød%20og%20øl"},
                {"Red, Rosé 14%",       "red,%20rosé%2014%25", "red,%20rosé%2014%25"},
                {"Red%2C%20Ros%C3%A9 14%25", "red%2c%20rosé%2014%25",  "red,%20rosé%2014%25"}
        };

        for (String[] test: TESTS) {
            assertEquals("The input '" + test[0] + "' should be normalised ambiguously as expected",
                         test[1], Normalisation.canonicaliseURL(test[0], true, false));
            assertEquals("The input '" + test[0] + "' should be normalised unambiguously as expected",
                         test[2], Normalisation.canonicaliseURL(test[0], true, true));
        }
    }

    @Test
    public void testFaultyHighOrderNormalisation() {
        final String[][] TESTS = new String[][]{
                {"Red, Rosé 14%",            "red,%20ros%c3%a9%2014%25", "red,%20rosé%2014%25"},
                {"red,%20ros%c3%a9%2014%25", "red,%20ros%c3%a9%2014%25", "red,%20rosé%2014%25"}
        };

        for (String[] test: TESTS) {
            assertEquals("The input '" + test[0] + "' should be normalised with high-order escaping as expected",
                         test[1], Normalisation.canonicaliseURL(test[0], false, true));
            assertEquals("The input '" + test[0] + "' should be normalised without high-order escaping as expected",
                         test[2], Normalisation.canonicaliseURL(test[0], true, true));
        }
    }

    @Test
    public void testFaultyHARDURLNormalisation() {
        final String[][] TESTS = new String[][]{
                {"http://example.com/%",         "http://example.com/%25"},
                {"http://example.com/%%25",      "http://example.com/%25%25"},
                {"http://example.com/10% proof", "http://example.com/10%25%20proof"},
                {"http://example.com/%a%2A",     "http://example.com/%25a*"},
                {"http://example.com/%g1%2A",    "http://example.com/%25g1*"},
        };

        for (String[] test: TESTS) {
            assertEquals("The input '" + test[0] + "' should be normalised and error-corrected as expected",
                         test[1], Normalisation.canonicaliseURL(test[0]));
        }
    }
}