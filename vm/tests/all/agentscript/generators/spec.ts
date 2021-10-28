export interface All {
    version: "1.0";
    debugpoints: At[];
}

export interface At {
    at: Where;
    actions: Op[];
    condition?: string;
}

export interface Where {
    name: string;
    line: number;
}


export interface Op {
    type: "watch" | "snapshot";
}

export interface WOp extends Op {
    type: "watch";
    depth?: number;
    id: string[] | string;
}

export interface SOp extends Op {
    type: "snapshot";
    framesLimit?: number;
    depth?: number;
}

