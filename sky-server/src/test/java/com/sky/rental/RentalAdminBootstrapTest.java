package com.sky.rental;
import com.sky.rental.config.RentalAdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RentalAdminBootstrapTest {
    @Test void missingCredentialsFailBeforeDatabaseAccess(){JdbcTemplate jdbc=mock(JdbcTemplate.class);assertThrows(IllegalArgumentException.class,()->new RentalAdminBootstrap(jdbc,"","short").run(null));verifyNoInteractions(jdbc);}
    @Test void existingEmployeeCannotBeOverwritten(){JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("new_staff"))).thenReturn(1);assertThrows(IllegalStateException.class,()->new RentalAdminBootstrap(jdbc,"new_staff","synthetic-password-123").run(null));verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void newEmployeeReceivesBcryptInsteadOfPlaintextOrMd5(){JdbcTemplate jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Integer.class),eq("new_staff"))).thenReturn(0);doAnswer(call->{String hash=call.getArgument(3);assertTrue(hash.startsWith("$2"));assertTrue(new BCryptPasswordEncoder().matches("synthetic-password-123",hash));return 1;}).when(jdbc).update(anyString(),eq("门店管理员"),eq("new_staff"),anyString());new RentalAdminBootstrap(jdbc,"new_staff","synthetic-password-123").run(null);verify(jdbc).update(anyString(),eq("门店管理员"),eq("new_staff"),anyString());}
}
