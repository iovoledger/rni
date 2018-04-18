package io.iovo.node.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class Alias {
    private Account account;
    private String alias;
    private String uri;
    private int timestamp;

}