import type {Admin} from "$lib/admin";

export class Household {
    id: string;
    name: string;
    image: string | undefined;
    admin: Admin;
    isSaved: boolean = false;

    constructor(id: string, name: string, admin: Admin) {
        this.id = id;
        this.name = name;
        this.admin = admin;
    }

}