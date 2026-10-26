package app.aavideoplayercar;

import org.junit.Test;
import java.util.List;
import java.util.HashSet;
import static org.junit.Assert.*;

public class ModuleRegistryTest {
    @Test public void recoveredDefaultOrderAndIdsStayStable() {
        assertEquals("16,5,6,18,1,2,3,9,10,11,13,17,8,12,14,4,15,19,20",
            ModuleRegistry.encode(ModuleRegistry.ordered(null)));
        assertEquals(19, new HashSet<>(ModuleRegistry.DEFAULTS).size());
        assertNull(ModuleRegistry.find(7));
    }
    @Test public void corruptedPreferencesCannotHideOrDuplicateModules() {
        List<ModuleRegistry.Module> result = ModuleRegistry.ordered("20,20,garbage,999, 5 ,-1,2147483648,,16");
        assertEquals(19, result.size());
        assertEquals(19, new HashSet<>(result).size());
        assertEquals(20, result.get(0).id);
        assertEquals(5, result.get(1).id);
        assertEquals(16, result.get(2).id);
        assertTrue(result.containsAll(ModuleRegistry.DEFAULTS));
    }
    @Test public void customOrderRoundTripsAndAppendsMissingModules() {
        List<ModuleRegistry.Module> result = ModuleRegistry.ordered("8,20,1");
        assertEquals(result, ModuleRegistry.ordered(ModuleRegistry.encode(result)));
        assertEquals(16, result.get(3).id);
        assertEquals(ModuleRegistry.DEFAULTS, ModuleRegistry.ordered(""));
    }
}
