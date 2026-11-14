package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import kr.ac.kookmin.seatforecast.account.data.Stops;
import kr.ac.kookmin.seatforecast.account.data.Validators;

public class ValidatorsTest {

    @Test
    public void email() {
        assertNull(Validators.email("student@kookmin.ac.kr"));
        assertNull(Validators.email("  a@b.co  "));
        assertNotNull(Validators.email(""));
        assertNotNull(Validators.email("no-at-sign"));
        assertNotNull(Validators.email("a@b"));
    }

    @Test
    public void password() {
        assertNull(Validators.password("123456"));
        assertNotNull(Validators.password("12345"));
        assertNotNull(Validators.password(null));
        assertNull(Validators.passwordConfirm("abcdef", "abcdef"));
        assertNotNull(Validators.passwordConfirm("abcdef", "abcdeg"));
    }

    @Test
    public void nickname() {
        assertNull(Validators.nickname("창록"));
        assertNotNull(Validators.nickname("   "));
        assertNotNull(Validators.nickname("가나다라마바사아자차카타파하가나다라마바사"));
    }

    @Test
    public void time() {
        assertTrue(Validators.isTime("09:00"));
        assertTrue(Validators.isTime("23:59"));
        assertFalse(Validators.isTime("9:00"));
        assertFalse(Validators.isTime("24:00"));
        assertFalse(Validators.isTime("09:60"));
    }

    @Test
    public void stopsMatchConfigPy() {
        // config.py: 자유총연맹에는 8800 이 서지 않음
        assertFalse(Stops.routesAt("203000161").contains("8800"));
        assertEquals(4, Stops.routesAt("203000063").size());
        assertEquals("매탄위브하늘채.경기아트센터", Stops.nameOf("203000162"));
        assertEquals("04007", Stops.find("203000063").mobileNo);
    }
}
