package com.example.apiscurrency.currency.dto;

public class CurrencyResponse {

    private String from;
    private String to;
    private double rate;

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }
}