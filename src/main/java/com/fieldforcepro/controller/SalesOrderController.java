package com.fieldforcepro.controller;

import com.fieldforcepro.model.SalesOrder;
import com.fieldforcepro.repository.SalesOrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sales-orders")
@Tag(name = "Sales Orders")
public class SalesOrderController {

    private final SalesOrderRepository salesOrderRepository;

    public SalesOrderController(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @GetMapping
    @Operation(summary = "List all sales orders with optional search on order number or customer name")
    public List<SalesOrder> list(@RequestParam(name = "search", required = false) String search) {
        List<SalesOrder> all = salesOrderRepository.findAll();
        if (search == null || search.isBlank()) {
            return all;
        }
        String term = search.toLowerCase();
        return all.stream()
                .filter(o ->
                        (o.getOrderNumber() != null && o.getOrderNumber().toLowerCase().contains(term)) ||
                        (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(term))
                )
                .toList();
    }
}
