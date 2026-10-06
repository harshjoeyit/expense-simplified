package io.github.harshjoeyit.cli.prompt;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

@Component
public class ExpensePromptProvider implements PromptProvider {

    @Override
    public AttributedString getPrompt() {
        return new AttributedString(
                "expsim:> ",
                AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold()
        );
    }
}
