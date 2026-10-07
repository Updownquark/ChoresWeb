interface User {
	readonly id: number;
	readonly email: string;
	readonly god: boolean;
	readonly globalAdmin: boolean;
}

export function usersEqual(user1: User, user2: User | null): boolean {
	if (user2 == null) return false;
	return (
		user1.id == user2.id && //
		user1.email == user2.email
	);
}

export default User;