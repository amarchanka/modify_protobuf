package org.modify.protobuf;

import protobuf_unittest.FirstData;
import protobuf_unittest.SecondData;

@ImplementsBy({ FirstData.class, SecondData.class })
public interface Data {

    boolean hasId();

    String getId();

    boolean hasRev();

    String getRev();

    boolean hasData();

    String getData();
}
