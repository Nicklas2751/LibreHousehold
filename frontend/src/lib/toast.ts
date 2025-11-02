export class Toast {
    id: number = Math.floor(Math.random() * 10000);
    type: string = "info";
    timeout: number = 3000;
    message: string;

    constructor(message: string, type?: string, timeout?: number) {
        this.message = message;
        if (type) this.type = type;
        if (timeout) this.timeout = timeout;
    }
}