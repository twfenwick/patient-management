package com.pm.stack;
import com.hashicorp.cdktf.TerraformStack;
import com.hashicorp.cdktf.providers.aws.vpc.Vpc;
import org.jetbrains.annotations.NotNull;
import software.constructs.Construct;

import java.util.Map;

public class LocalStackTf extends TerraformStack {
    private final Vpc vpc;

    public LocalStackTf(@NotNull Construct scope, @NotNull String id) {
        super(scope, id);

        this.vpc = createVpc();
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManageMentVPC")
                .tags(Map.of("Name", "PatientManagementVPC"))
                .cidrBlock("10.0.0.0/16")
                .build();
    }
}
