/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.github.sviperll.maven.profiledep;

import java.io.IOException;

/**
 *
 * @author vir
 */
public class IndentationStringBuilder implements Appendable {
    private final StringBuilder builder = new StringBuilder();

    @Override
    public IndentationStringBuilder append(CharSequence csq) {
        builder.append(csq);
        return this;
    }

    @Override
    public IndentationStringBuilder append(CharSequence csq, int start, int end) {
        builder.append(csq, start, end);
        return this;
    }

    @Override
    public IndentationStringBuilder append(char c) {
        builder.append(c);
        return this;
    }

    public IndentationStringBuilder startNewLine(int identation) {
        builder.append("\n");
        for (int i = 0; i < identation; i++)
            builder.append("    ");
        return this;
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
