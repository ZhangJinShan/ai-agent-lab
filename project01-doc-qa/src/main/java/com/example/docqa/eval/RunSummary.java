package com.example.docqa.eval;


public record RunSummary(int total, int correct, int judgeErrors, int answerErrors) {

    public double accuracy(){
        return total == 0 ? 0.0 : (double) correct / total;
    }
}
