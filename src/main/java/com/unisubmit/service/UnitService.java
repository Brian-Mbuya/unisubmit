package com.unisubmit.service;

import com.unisubmit.domain.Department;
import com.unisubmit.domain.Unit;
import com.unisubmit.repository.DepartmentRepository;
import com.unisubmit.repository.UnitRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UnitService {

    private final UnitRepository unitRepository;
    private final DepartmentRepository departmentRepository;

    public UnitService(UnitRepository unitRepository, DepartmentRepository departmentRepository) {
        this.unitRepository = unitRepository;
        this.departmentRepository = departmentRepository;
    }

    public Unit createUnit(Long departmentId, String unitCode, String unitName, Integer creditHours) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        
        Unit unit = new Unit();
        unit.setDepartment(dept);
        unit.setUnitCode(unitCode);
        unit.setUnitName(unitName);
        unit.setCreditHours(creditHours != null ? creditHours : 3);
        return unitRepository.save(unit);
    }
    
    public Unit updateUnit(Long unitId, Long departmentId, String unitCode, String unitName, Integer creditHours) {
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new RuntimeException("Unit not found"));
        
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        
        unit.setDepartment(dept);
        unit.setUnitCode(unitCode);
        unit.setUnitName(unitName);
        unit.setCreditHours(creditHours != null ? creditHours : 3);
        return unitRepository.save(unit);
    }
    
    public void deleteUnit(Long unitId) {
        unitRepository.deleteById(unitId);
    }

    public List<Unit> findAllUnits() {
        return unitRepository.findAll();
    }
}
