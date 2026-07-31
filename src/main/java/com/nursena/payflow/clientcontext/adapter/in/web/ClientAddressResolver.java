package com.nursena.payflow.clientcontext.adapter.in.web;

import com.nursena.payflow.clientcontext.domain.ResolvedClientAddress;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface ClientAddressResolver {

    ResolvedClientAddress resolve(
        HttpServletRequest request
    );
}
