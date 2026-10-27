package com.sky.rental;
import com.sky.exception.BaseException;
import com.sky.rental.auth.RentalContext;
import com.sky.rental.mapper.RentalCatalogMapper;
import com.sky.rental.model.*;
import com.sky.rental.service.RentalCatalogService;
import org.junit.jupiter.api.*;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RentalCatalogServiceTest {
    RentalCatalogMapper mapper;RentalCatalogService service;
    @BeforeEach void init() { mapper=mock(RentalCatalogMapper.class);service=new RentalCatalogService(mapper);RentalContext.set(1L,"ADMIN","test","test"); }
    @AfterEach void clean() { RentalContext.clear(); }
    RentalRequests.Equipment equipment() { RentalRequests.Equipment e=new RentalRequests.Equipment();e.setCategoryId(1L);e.setName("相机");e.setDailyRate(new BigDecimal("10.00"));e.setDeposit(new BigDecimal("100.00"));e.setStatus(1);RentalCategory c=new RentalCategory();c.setId(1L);c.setStatus(1);when(mapper.category(1L)).thenReturn(c);return e; }
    @Test void rejectsInvalidMoneyAndMissingCategory() { RentalRequests.Equipment e=equipment();e.setDailyRate(new BigDecimal("0"));assertThrows(BaseException.class,()->service.saveEquipment(null,e));e.setDailyRate(new BigDecimal("0.001"));assertThrows(BaseException.class,()->service.saveEquipment(null,e));verify(mapper,never()).insertEquipment(any()); }
    @Test void updateLocksEquipmentAndSetsAuditIdentity() { RentalRequests.Equipment request=equipment();RentalEquipment e=new RentalEquipment();e.setId(2L);when(mapper.lockEquipment(2L)).thenReturn(e);service.saveEquipment(2L,request);InOrder order=inOrder(mapper);order.verify(mapper).lockEquipment(2L);order.verify(mapper).updateEquipment(e);assertEquals(1L,e.getUpdateUser());assertNotNull(e.getUpdatedAt()); }
    @Test void assetsCannotBeTakenOfflineWithOccupyingOrder() { RentalAsset a=new RentalAsset();a.setId(2L);a.setEquipmentId(1L);when(mapper.asset(2L)).thenReturn(a);when(mapper.lockAsset(2L)).thenReturn(a);when(mapper.occupyingOrders(2L)).thenReturn(Collections.singletonList(8L));RentalRequests.AssetStatus r=new RentalRequests.AssetStatus();r.setStatus("MAINTENANCE");assertThrows(BaseException.class,()->service.assetStatus(2L,r));InOrder order=inOrder(mapper);order.verify(mapper).lockEquipment(1L);order.verify(mapper).lockAsset(2L);verify(mapper,never()).updateAsset(any()); }
    @Test void newAssetLocksParentAndRejectsDuplicateCode() { RentalRequests.Asset r=new RentalRequests.Asset();r.setEquipmentId(1L);r.setAssetCode("CAM-1");when(mapper.lockEquipment(1L)).thenReturn(new RentalEquipment());doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertAsset(any());assertThrows(BaseException.class,()->service.addAsset(r));InOrder order=inOrder(mapper);order.verify(mapper).lockEquipment(1L);order.verify(mapper).insertAsset(any()); }
    @Test void disabledEquipmentHiddenFromPublic() { RentalEquipment e=new RentalEquipment();e.setStatus(0);when(mapper.getEquipment(1L)).thenReturn(e);assertThrows(BaseException.class,()->service.equipment(1L,false));assertSame(e,service.equipment(1L,true)); }
    @Test void paginationBounded() { assertThrows(BaseException.class,()->service.equipment(false,0,12,null,null));assertThrows(BaseException.class,()->service.equipment(false,1,101,null,null));verifyNoInteractions(mapper); }
    @Test void invalidAssetStateRejectedBeforeDatabase() { RentalRequests.AssetStatus r=new RentalRequests.AssetStatus();r.setStatus("INVALID");assertThrows(BaseException.class,()->service.assetStatus(2L,r));verifyNoInteractions(mapper); }
    @Test void customerCannotReadPrivateCatalogOrWriteThroughService() {
        RentalContext.set(2L,"USER","customer","customer");
        assertThrows(BaseException.class,()->service.categories(true));
        assertThrows(BaseException.class,()->service.assets(1L));
        assertThrows(BaseException.class,()->service.saveCategory(null,new RentalRequests.Category()));
        verifyNoInteractions(mapper);
    }
    @Test void missingIdentityCannotWriteCatalog() {
        RentalContext.clear();
        assertThrows(BaseException.class,()->service.addAsset(new RentalRequests.Asset()));
        verifyNoInteractions(mapper);
    }
}
